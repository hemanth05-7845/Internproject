import asyncio
import logging
import os

import httpx

from fastapi import (
    APIRouter,
    Depends,
    Header,
    HTTPException,
    WebSocket,
    WebSocketDisconnect,
    status,
)

from app.core.security.jwt_handler import create_access_token, decode_access_token
from app.services.spring_client.engine_client import (
    create_room as engine_create_room,
    join_room_by_code as engine_join_by_code,
    get_room_by_code as engine_get_room,
    get_players_by_code as engine_get_players,
    start_game as engine_start_game,
    get_game_state as engine_get_game_state,
    advance_phase as engine_advance_phase,
    resolve_voting as engine_resolve_voting,
    submit_night_kill as engine_night_kill,
    submit_police_guess as engine_police_guess,
    submit_doctor_save as engine_doctor_save,
    submit_vote as engine_submit_vote,
    send_message as engine_send_message,
)
from app.services.gin_client.event_client import get_timer as gin_get_timer, start_phase_timer as gin_start_phase_timer
from app.dto.request.models import (
    JoinRequest,
    CreateRoomRequest,
    JoinRoomRequest,
    MessageRequest,
    VoteRequest,
    NightKillRequest,
    PoliceGuessRequest,
    DoctorSaveRequest,
    AdvancePhaseRequest,
    ResolveVotingRequest,
)
from app.dto.response.models import (
    ApiResponse,
    GameSnapshot,
    RoomResponse,
    TokenResponse,
)
from app.services.game_orchestrator.room_store import (
    register_session,
    cache_room,
    get_host,
    is_host,
)

logger = logging.getLogger(__name__)

router = APIRouter()

SECRET = os.getenv("JWT_SECRET")
ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
EXPIRES_MINUTES = int(os.getenv("JWT_EXPIRES_MINUTES", "480"))
WS_POLL_INTERVAL = 1.5
MIN_PLAYERS_DEFAULT = 6

# Duration (seconds) for each timed phase. Adjust here to tune the game pacing.
PHASE_DURATION_SECONDS: dict[str, int] = {
    "NIGHT":          30,
    "POLICE_GUESS":   30,
    "DOCTOR_SAVE":    30,
    "DAY_DISCUSSION": 60,
    "VOTING":         30,
    "SUNRISE":        15,
}


def get_current_user(authorization: str = Header(default="")) -> str:
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")
    token = authorization.removeprefix("Bearer ")
    return decode_access_token(token, SECRET, ALGORITHM)


def _room_response(room: dict) -> RoomResponse:
    return RoomResponse(
        room_id=room["roomId"],
        room_code=room["roomCode"],
        room_name=room["roomName"],
        host_username=room["hostUsername"],
        player_count=room["playerCount"],
        min_players=room.get("minPlayers", MIN_PLAYERS_DEFAULT),
        status=room["status"],
    )


def _mask_players(players: list, username: str, game_over: bool) -> tuple[list, dict | None, list]:
    masked = []
    my_player = None
    mafia_members = []
    for p in players:
        role = p.get("role")
        is_me = p.get("name") == username
        if is_me:
            my_player = p
        masked.append({"name": p.get("name"), "alive": p.get("alive", True), "role": role if (game_over or is_me) else None})
        if role == "MAFIA":
            mafia_members.append(p.get("name"))
    return masked, my_player, mafia_members


def _resolve_vote_eligibility(snap: dict, my_player: dict | None, current_day: int) -> bool:
    if snap.get("phase") != "VOTING":
        return False
    is_alive = bool(my_player and my_player.get("alive", False))
    vote_eligible_day = my_player.get("voteEligibleDayNumber") if my_player else None
    return is_alive or vote_eligible_day == current_day


def _build_snapshot(snap: dict, username: str) -> dict:
    game_over = snap.get("phase") == "GAME_OVER"
    current_day = snap.get("dayNumber", 0)
    players, my_player, mafia_members = _mask_players(snap.get("players", []), username, game_over)
    my_role = my_player.get("role") if my_player else None
    return {
        "phase": snap.get("phase", "LOBBY"),
        "day_number": current_day,
        "night_number": snap.get("nightNumber", 0),
        "players": players,
        "alive_players": snap.get("alivePlayers", []),
        "eliminated_players": snap.get("eliminatedPlayers", []),
        "can_vote": _resolve_vote_eligibility(snap, my_player, current_day),
        "night_kill_target": snap.get("nightKillTarget"),
        "night_kill_failed": snap.get("nightKillFailed"),
        "police_guess_target": snap.get("policeGuessTarget"),
        "police_guess_correct": snap.get("policeGuessCorrect"),
        "winner": snap.get("winner", "NONE"),
        "chat_messages": snap.get("chatMessages", []),
        "events": snap.get("events", []),
        "allowed_actions": snap.get("allowedActions", []),
        "room_code": snap.get("roomCode", ""),
        "host_username": snap.get("hostUsername", ""),
        "my_role": my_role,
        "mafia_members": mafia_members if my_role == "MAFIA" else [],
    }


async def _get_timer_data(room_id: str) -> dict:
    try:
        timer = await gin_get_timer(room_id)
        return {
           "phase_ends_at": timer.get("updatedAt", ""),
            "remaining_seconds": timer.get("remainingTime", 0),
        }
    except Exception:
        logger.warning("Timer service unavailable for room_id=%s, defaulting to empty", room_id)
        return {"phase_ends_at": "", "remaining_seconds": 0}


async def _proxy_action(coro, log_msg: str, room_id: str) -> ApiResponse:
    try:
        await coro
    except Exception as exc:
        logger.exception("%s room_id=%s", log_msg, room_id)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc
    return ApiResponse(message="OK")


@router.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "mafia-gateway"}


@router.post("/auth/join", response_model=TokenResponse)
def join(payload: JoinRequest) -> TokenResponse:
    register_session(payload.username)
    token = create_access_token(subject=payload.username, secret=SECRET, algorithm=ALGORITHM, expires_minutes=EXPIRES_MINUTES)
    return TokenResponse(access_token=token)


@router.post("/create-room", response_model=RoomResponse)
async def create_room_endpoint(payload: CreateRoomRequest, username: str = Depends(get_current_user)) -> RoomResponse:
    try:
        room = await engine_create_room(payload.room_name, username)
    except Exception as exc:
        logger.exception("Failed to create room for user=%s", username)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc
    cache_room(room["roomCode"], room["roomId"], room["hostUsername"])
    return _room_response(room)


@router.post("/join-room", response_model=RoomResponse)
async def join_room_endpoint(payload: JoinRoomRequest, username: str = Depends(get_current_user)) -> RoomResponse:
    room_code = payload.room_code.upper()
    try:
        room = await engine_join_by_code(room_code, username)
    except Exception as exc:
        logger.warning("Room not found: code=%s user=%s error=%s", room_code, username, exc)
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"Room not found: {exc}") from exc
    cache_room(room["roomCode"], room["roomId"], room["hostUsername"])
    return _room_response(room)


@router.get("/room/{room_code}/players")
async def get_room_players(room_code: str, _: str = Depends(get_current_user)) -> dict:
    code = room_code.upper()
    try:
        players, room = await asyncio.gather(engine_get_players(code), engine_get_room(code))
    except Exception as exc:
        logger.warning("Failed to fetch players for room_code=%s: %s", code, exc)
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    min_players = room.get("minPlayers", MIN_PLAYERS_DEFAULT)
    return {
        "players": players,
        "playerCount": len(players),
        "minPlayers": min_players,
        "readyToStart": len(players) >= min_players,
        "hostUsername": room.get("hostUsername", ""),
    }


@router.post("/start-game", response_model=ApiResponse)
async def start_game_endpoint(payload: AdvancePhaseRequest, username: str = Depends(get_current_user)) -> ApiResponse:
    if get_host(payload.room_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Room not found in cache")
    if not is_host(payload.room_id, username):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the host can start the game")
    try:
        await engine_start_game(payload.room_id)
    except httpx.HTTPStatusError as exc:
        logger.exception("Failed to start game room_id=%s", payload.room_id)
        msg = "Failed to start game"
        try:
            data = exc.response.json()
            msg = data.get("message") or data.get("detail") or msg
        except Exception:
            if exc.response.text:
                msg = exc.response.text
        raise HTTPException(status_code=exc.response.status_code, detail=msg) from exc
    except Exception as exc:
        logger.exception("Failed to start game room_id=%s", payload.room_id)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc

    # Start the timer for the first phase (NIGHT)
    try:
        snap = await engine_get_game_state(payload.room_id)
        first_phase = snap.get("phase", "NIGHT")
        duration = PHASE_DURATION_SECONDS.get(first_phase)
        if duration:
            await gin_start_phase_timer(payload.room_id, first_phase, duration)
    except Exception as exc:
        logger.warning("Failed to start initial phase timer room_id=%s: %s", payload.room_id, exc)

    return ApiResponse(message="Game started")


@router.get("/game-state/{room_id}", response_model=GameSnapshot)
async def game_state(room_id: str, username: str = Depends(get_current_user)) -> GameSnapshot:
    try:
        snap = await engine_get_game_state(room_id)
    except Exception as exc:
        logger.exception("Backend unavailable for room_id=%s", room_id)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=f"Backend unavailable: {exc}") from exc
    snapshot = {**_build_snapshot(snap, username), **await _get_timer_data(room_id)}
    return GameSnapshot(**snapshot)


@router.post("/advance-phase", response_model=ApiResponse)
async def advance_phase(payload: AdvancePhaseRequest, username: str = Depends(get_current_user)) -> ApiResponse:
    try:
        await engine_advance_phase(payload.room_id)
    except Exception as exc:
        logger.exception("Failed to advance phase room_id=%s", payload.room_id)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc

    # Start a fresh countdown for the new phase
    try:
        snap = await engine_get_game_state(payload.room_id)
        new_phase = snap.get("phase", "")
        duration = PHASE_DURATION_SECONDS.get(new_phase)
        if duration:
            await gin_start_phase_timer(payload.room_id, new_phase, duration)
            logger.info("Started timer room_id=%s phase=%s duration=%ds", payload.room_id, new_phase, duration)
    except Exception as exc:
        # Timer failure is non-fatal — game can still proceed manually
        logger.warning("Failed to start phase timer room_id=%s: %s", payload.room_id, exc)

    return ApiResponse(message="OK")


@router.post("/resolve-voting", response_model=ApiResponse)
async def resolve_voting(payload: ResolveVotingRequest, username: str = Depends(get_current_user)) -> ApiResponse:
    return await _proxy_action(engine_resolve_voting(payload.room_id), "Failed to resolve voting", payload.room_id)


@router.post("/night-kill", response_model=ApiResponse)
async def night_kill(payload: NightKillRequest, username: str = Depends(get_current_user)) -> ApiResponse:
    return await _proxy_action(engine_night_kill(payload.room_id, payload.target_player), "Night kill failed", payload.room_id)


@router.post("/police-guess", response_model=ApiResponse)
async def police_guess(payload: PoliceGuessRequest, username: str = Depends(get_current_user)) -> ApiResponse:
    return await _proxy_action(engine_police_guess(payload.room_id, payload.suspect_player), "Police guess failed", payload.room_id)


@router.post("/doctor-save", response_model=ApiResponse)
async def doctor_save(payload: DoctorSaveRequest, username: str = Depends(get_current_user)) -> ApiResponse:
    return await _proxy_action(engine_doctor_save(payload.room_id, payload.saved_player), "Doctor save failed", payload.room_id)


@router.post("/submit-vote", response_model=ApiResponse)
async def submit_vote(payload: VoteRequest, username: str = Depends(get_current_user)) -> ApiResponse:
    return await _proxy_action(engine_submit_vote(payload.room_id, username, payload.target_player), "Vote submission failed", payload.room_id)


@router.post("/send-message", response_model=ApiResponse)
async def send_message(payload: MessageRequest, username: str = Depends(get_current_user)) -> ApiResponse:
    return await _proxy_action(engine_send_message(payload.room_id, username, payload.message), "Message send failed", payload.room_id)


@router.websocket("/ws/{room_id}")
async def game_websocket(websocket: WebSocket, room_id: str, token: str = "") -> None:
    try:
        username = decode_access_token(token, SECRET, ALGORITHM)
    except Exception:
        await websocket.close(code=1008)
        return

    await websocket.accept()

    try:
        while True:
            try:
                raw = await engine_get_game_state(room_id)
                snapshot = {**_build_snapshot(raw, username), **await _get_timer_data(room_id)}
                await websocket.send_json(snapshot)
            except WebSocketDisconnect:
                return
            except Exception as exc:
                logger.warning("WS poll error room_id=%s user=%s: %s", room_id, username, exc)
            await asyncio.sleep(WS_POLL_INTERVAL)
    except WebSocketDisconnect:
        logger.info("WS client disconnected room_id=%s user=%s", room_id, username)
    except Exception as exc:
        logger.exception("WS session crashed room_id=%s user=%s: %s", room_id, username, exc)
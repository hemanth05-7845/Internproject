import asyncio
import os
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, WebSocket, WebSocketDisconnect, status

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
from app.services.gin_client.event_client import get_timer as gin_get_timer
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
from app.dto.response.models import ApiResponse, GameSnapshot, RoomResponse, TokenResponse
from app.services.game_orchestrator.room_store import (
    register_session,
    cache_room,
    get_room_id,
    is_host,
)

router = APIRouter()

SECRET = os.getenv("JWT_SECRET", "dev-secret")
ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
EXPIRES_MINUTES = int(os.getenv("JWT_EXPIRES_MINUTES", "480"))


# ---- Auth dependency ----

def get_current_user(authorization: str = Header(default="")) -> str:
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED,
                            detail="Missing bearer token")
    token = authorization.replace("Bearer ", "", 1)
    return decode_access_token(token, SECRET, ALGORITHM)


# ---- Health ----

@router.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "mafia-gateway"}


# ---- Auth ----

@router.post("/auth/join", response_model=TokenResponse)
def join(payload: JoinRequest) -> TokenResponse:
    """
    Name-only session: player sends their chosen display name,
    gets back a JWT.  No password required.
    """
    register_session(payload.username)
    token = create_access_token(
        subject=payload.username,
        secret=SECRET,
        algorithm=ALGORITHM,
        expires_minutes=EXPIRES_MINUTES,
    )
    return TokenResponse(access_token=token)


# ---- Room management ----

@router.post("/create-room", response_model=RoomResponse)
async def create_room_endpoint(payload: CreateRoomRequest,
                               username: str = Depends(get_current_user)) -> RoomResponse:
    try:
        room = await engine_create_room(payload.room_name, username)
        cache_room(room["roomCode"], room["roomId"], room["hostUsername"])
        return RoomResponse(
            room_id=room["roomId"],
            room_code=room["roomCode"],
            room_name=room["roomName"],
            host_username=room["hostUsername"],
            player_count=room["playerCount"],
            min_players=room.get("minPlayers", 6),
            status=room["status"],
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/join-room", response_model=RoomResponse)
async def join_room_endpoint(payload: JoinRoomRequest,
                             username: str = Depends(get_current_user)) -> RoomResponse:
    try:
        room = await engine_join_by_code(payload.room_code.upper(), username)
        cache_room(room["roomCode"], room["roomId"], room["hostUsername"])
        return RoomResponse(
            room_id=room["roomId"],
            room_code=room["roomCode"],
            room_name=room["roomName"],
            host_username=room["hostUsername"],
            player_count=room["playerCount"],
            min_players=room.get("minPlayers", 6),
            status=room["status"],
        )
    except Exception as exc:
        raise HTTPException(status_code=404, detail=f"Room not found: {exc}") from exc


@router.get("/room/{room_code}/players")
async def get_room_players(room_code: str,
                           _: str = Depends(get_current_user)) -> dict:
    try:
        players = await engine_get_players(room_code.upper())
        room = await engine_get_room(room_code.upper())
        return {
            "players": players,
            "playerCount": len(players),
            "minPlayers": room.get("minPlayers", 6),
            "readyToStart": len(players) >= room.get("minPlayers", 6),
            "hostUsername": room.get("hostUsername", ""),
        }
    except Exception as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


# ---- Game start ----

@router.post("/start-game", response_model=ApiResponse)
async def start_game_endpoint(payload: AdvancePhaseRequest,
                              username: str = Depends(get_current_user)) -> ApiResponse:
    room_id = get_room_id(payload.room_id)  
    if room_id and not is_host(room_id, username):
        raise HTTPException(status_code=403, detail="Only the host can start the game")
    try:
        await engine_start_game(payload.room_id)
        return ApiResponse(message="Game started")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


# ---- Polling endpoint ----

@router.get("/game-state/{room_id}", response_model=GameSnapshot)
async def game_state(room_id: str, username: str = Depends(get_current_user)) -> GameSnapshot:
    try:
        snap = await engine_get_game_state(room_id)
        snapshot = _build_snapshot(snap, username)
        phase_ends_at = ""
        try:
            timer = await gin_get_timer(room_id)
            snapshot["phase_ends_at"] = timer.get("updatedAt", "")
        except Exception:
            pass

        return GameSnapshot(
            **snapshot,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Backend unavailable: {exc}") from exc


# ---- Phase control (host only) ----

@router.post("/advance-phase", response_model=ApiResponse)
async def advance_phase(payload: AdvancePhaseRequest,
                        username: str = Depends(get_current_user)) -> ApiResponse:
    try:
        await engine_advance_phase(payload.room_id)
        return ApiResponse(message="Phase advanced")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/resolve-voting", response_model=ApiResponse)
async def resolve_voting(payload: ResolveVotingRequest,
                         username: str = Depends(get_current_user)) -> ApiResponse:
    try:
        await engine_resolve_voting(payload.room_id)
        return ApiResponse(message="Voting resolved")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


# ---- Night actions ----

@router.post("/night-kill", response_model=ApiResponse)
async def night_kill(payload: NightKillRequest,
                     username: str = Depends(get_current_user)) -> ApiResponse:
    try:
        await engine_night_kill(payload.room_id, payload.target_player)
        return ApiResponse(message="Night kill recorded")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/police-guess", response_model=ApiResponse)
async def police_guess(payload: PoliceGuessRequest,
                       username: str = Depends(get_current_user)) -> ApiResponse:
    try:
        await engine_police_guess(payload.room_id, payload.suspect_player)
        return ApiResponse(message="Police guess recorded")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/doctor-save", response_model=ApiResponse)
async def doctor_save(payload: DoctorSaveRequest,
                      username: str = Depends(get_current_user)) -> ApiResponse:
    try:
        await engine_doctor_save(payload.room_id, payload.saved_player)
        return ApiResponse(message="Doctor save recorded")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


# ---- Voting ----

@router.post("/submit-vote", response_model=ApiResponse)
async def submit_vote(payload: VoteRequest,
                      username: str = Depends(get_current_user)) -> ApiResponse:
    try:
        await engine_submit_vote(payload.room_id, username, payload.target_player)
        return ApiResponse(message="Vote submitted")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


# ---- Chat ----

@router.post("/send-message", response_model=ApiResponse)
async def send_message(payload: MessageRequest,
                       username: str = Depends(get_current_user)) -> ApiResponse:
    try:
        await engine_send_message(payload.room_id, username, payload.message)
        return ApiResponse(message="Message sent")
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


# ── WebSocket: push live game state to browser ──────────────────────────────
# Frontend connects to  ws://localhost:8000/ws/{room_id}?token=<jwt>
# Gateway polls Spring every 1.5 s and pushes personalised snapshot.

def _build_snapshot(snap: dict, username: str) -> dict:
    """Convert camelCase Spring snapshot → snake_case personalised dict."""
    game_over = snap.get("phase") == "GAME_OVER"
    current_day = snap.get("dayNumber", 0)
    players = []
    my_role = None
    my_player = None
    for p in snap.get("players", []):
        role = p.get("role")
        if p.get("name") == username:
            my_role = role
            my_player = p
        players.append({
            "name":  p.get("name"),
            "alive": p.get("alive", True),
            "role":  role if (game_over or p.get("name") == username) else None,
        })
    is_alive = bool(my_player and my_player.get("alive", False))
    vote_eligible_day = my_player.get("voteEligibleDayNumber") if my_player else None
    return {
        "phase":               snap.get("phase", "LOBBY"),
        "day_number":          snap.get("dayNumber", 0),
        "night_number":        snap.get("nightNumber", 0),
        "players":             players,
        "alive_players":       snap.get("alivePlayers", []),
        "eliminated_players":  snap.get("eliminatedPlayers", []),
        "can_vote":            snap.get("phase") == "VOTING" and (is_alive or vote_eligible_day == current_day),
        "night_kill_target":   snap.get("nightKillTarget"),

        "night_kill_failed":   snap.get("nightKillFailed"),
        "police_guess_target": snap.get("policeGuessTarget"),
        "police_guess_correct":snap.get("policeGuessCorrect"),
        "winner":              snap.get("winner", "NONE"),
        "chat_messages":       snap.get("chatMessages", []),
        "events":              snap.get("events", []),
        "allowed_actions":     snap.get("allowedActions", []),
        "room_code":           snap.get("roomCode", ""),
        "host_username":       snap.get("hostUsername", ""),
        "my_role":             my_role,
    }


@router.websocket("/ws/{room_id}")
async def game_websocket(websocket: WebSocket, room_id: str, token: str = ""):
    # Validate JWT
    try:
        username = decode_access_token(token, SECRET, ALGORITHM)
    except Exception:
        await websocket.close(code=1008)
        return

    await websocket.accept()
    try:
        while True:
            try:
                raw      = await engine_get_game_state(room_id)
                snapshot = _build_snapshot(raw, username)
                await websocket.send_json(snapshot)
            except WebSocketDisconnect:
                return  
            except Exception:
                pass    
            await asyncio.sleep(1.5)
    except (WebSocketDisconnect, Exception):
        pass

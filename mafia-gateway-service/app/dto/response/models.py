from datetime import datetime, timezone
from pydantic import BaseModel
from typing import Optional


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


class ApiResponse(BaseModel):
    message: str
    data: Optional[dict] = None


class RoomResponse(BaseModel):

    room_id: str
    room_code: str
    room_name: str
    host_username: str
    player_count: int
    min_players: int = 6
    status: str


class PlayerInfo(BaseModel):
    name: str
    alive: bool
    role: Optional[str] = None


class ChatMessage(BaseModel):
    sender: str
    message: str
    timestamp: str


class GameEvent(BaseModel):
    type: str
    description: str
    at: str


class GameSnapshot(BaseModel):

    phase: str
    day_number: int = 0
    night_number: int = 0
    players: list[dict]
    alive_players: list[str]
    eliminated_players: list[str]
    can_vote: bool = False
    night_kill_target: Optional[str] = None
    night_kill_failed: Optional[bool] = None
    police_guess_target: Optional[str] = None
    police_guess_correct: Optional[bool] = None
    winner: str = "NONE"
    chat_messages: list[dict]
    events: list[dict]
    allowed_actions: list[str]
    room_code: str = ""
    host_username: str = ""
    phase_ends_at: str = ""
    my_role: Optional[str] = None


def default_snapshot() -> GameSnapshot:
    return GameSnapshot(
        phase="LOBBY",
        players=[],
        alive_players=[],
        eliminated_players=[],
        winner="NONE",
        chat_messages=[],
        events=[],
        allowed_actions=["join_room", "leave_room"],
        phase_ends_at=datetime.now(timezone.utc).isoformat(),
    )

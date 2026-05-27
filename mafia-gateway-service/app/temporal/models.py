from __future__ import annotations
from dataclasses import dataclass, field


@dataclass
class GameStartedSignal:
    first_phase: str         
    host_username: str
    room_code: str


@dataclass
class PhaseAdvancedSignal:
    new_phase: str            


@dataclass
class GameEndedSignal:
    winner: str               


@dataclass
class AbandonRoomSignal:
    reason: str = "players_dropped"


@dataclass
class RoomStateQuery:
    pass


@dataclass
class RoomState:
    room_id: str
    room_code: str
    host_username: str
    phase: str = "LOBBY"
    winner: str = "NONE"
    is_active: bool = False
    is_game_over: bool = False
    phase_history: list[str] = field(default_factory=list)

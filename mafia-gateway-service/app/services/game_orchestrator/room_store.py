"""
Gateway-level in-memory store for sessions and room metadata.
The source of truth for game state is the Spring engine (MongoDB).
This store only holds:
  - registered usernames → JWT issued (no password)
  - room_code → room_id mapping cache (populated on create/join)
  - room_id → host_username mapping (so gateway can check host privileges)
"""
from __future__ import annotations

# username → True (just tracks that a session was issued)
SESSIONS: dict[str, bool] = {}

# roomCode (upper) → roomId
CODE_TO_ID: dict[str, str] = {}

# roomId → hostUsername
ROOM_HOSTS: dict[str, str] = {}


def register_session(username: str) -> None:
    SESSIONS[username] = True


def session_exists(username: str) -> bool:
    return username in SESSIONS


def cache_room(room_code: str, room_id: str, host_username: str) -> None:
    CODE_TO_ID[room_code.upper()] = room_id
    ROOM_HOSTS[room_id] = host_username


def get_room_id(room_code: str) -> str | None:
    return CODE_TO_ID.get(room_code.upper())


def get_host(room_id: str) -> str | None:
    return ROOM_HOSTS.get(room_id)


def is_host(room_id: str, username: str) -> bool:
    return ROOM_HOSTS.get(room_id) == username

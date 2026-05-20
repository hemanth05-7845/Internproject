from __future__ import annotations

_SESSIONS: dict[str, bool] = {}

_CODE_TO_ID: dict[str, str] = {}

_ROOM_HOSTS: dict[str, str] = {}


def register_session(username: str) -> None:
    _SESSIONS[username] = True


def cache_room(room_code: str, room_id: str, host_username: str) -> None:
    _CODE_TO_ID[room_code.upper()] = room_id
    _ROOM_HOSTS[room_id] = host_username


def get_room_id(room_code: str) -> str | None:
    return _CODE_TO_ID.get(room_code.upper())


def get_host(room_id: str) -> str | None:
    return _ROOM_HOSTS.get(room_id)


def is_host(room_id: str, username: str) -> bool:
    return _ROOM_HOSTS.get(room_id) == username


def reset() -> None:
    _SESSIONS.clear()
    _CODE_TO_ID.clear()
    _ROOM_HOSTS.clear()
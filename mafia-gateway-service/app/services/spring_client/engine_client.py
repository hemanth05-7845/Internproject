import os

import httpx

_ENGINE_HOST = os.getenv("SPRING_ENGINE_BASE_URL")
_SPRING_BASE_URL = f"{_ENGINE_HOST}/api"

_client = httpx.AsyncClient(base_url=_SPRING_BASE_URL, timeout=5.0)


async def _get(path: str) -> dict | list:
    r = await _client.get(path)
    r.raise_for_status()
    return r.json()


async def _post(path: str, body: dict | None = None) -> dict:
    r = await _client.post(path, json=body or {})
    r.raise_for_status()
    return r.json()


async def create_room(room_name: str, host_username: str) -> dict:
    return await _post(
        "/rooms/create",
        {"roomName": room_name, "hostUsername": host_username},
    )


async def join_room_by_code(room_code: str, username: str) -> dict:
    return await _post(
        "/rooms/join-by-code",
        {"roomCode": room_code, "username": username},
    )


async def get_room_by_code(room_code: str) -> dict:
    return await _get(f"/rooms/by-code/{room_code}")


async def get_players_by_code(room_code: str) -> list:
    return await _get(f"/rooms/by-code/{room_code}/players")


async def start_game(room_id: str) -> dict:
    return await _post(f"/game-state/{room_id}/start")


async def get_game_state(room_id: str) -> dict:
    return await _get(f"/game-state/{room_id}")


async def advance_phase(room_id: str) -> dict:
    return await _post(f"/game/{room_id}/advance-phase")


async def resolve_voting(room_id: str) -> dict:
    return await _post(f"/game/{room_id}/resolve-voting")


async def submit_night_kill(room_id: str, target_player: str) -> dict:
    return await _post(
        f"/game/{room_id}/submit-night-kill",
        {"targetPlayer": target_player},
    )


async def submit_police_guess(room_id: str, suspect_player: str) -> dict:
    return await _post(
        f"/game/{room_id}/submit-police-guess",
        {"suspectPlayer": suspect_player},
    )


async def submit_doctor_save(room_id: str, saved_player: str) -> dict:
    return await _post(
        f"/game/{room_id}/submit-doctor-save",
        {"savedPlayer": saved_player},
    )


async def submit_vote(room_id: str, voter_id: str, target_player: str) -> dict:
    return await _post(
        f"/rooms/{room_id}/vote",
        {"voterId": voter_id, "targetPlayerId": target_player},
    )


async def send_message(room_id: str, sender_username: str, content: str) -> dict:
    return await _post(
        f"/rooms/{room_id}/message",
        {"senderUsername": sender_username, "content": content},
    )

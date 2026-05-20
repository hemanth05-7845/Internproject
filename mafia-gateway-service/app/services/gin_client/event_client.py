import os

import httpx

_GIN_BASE_URL = os.getenv("GIN_EVENT_BASE_URL", "http://localhost:8081/api").rstrip("/")

_client = httpx.AsyncClient(base_url=_GIN_BASE_URL, timeout=5.0)


async def get_timer(room_id: str) -> dict:
    response = await _client.get(f"/timer/{room_id}")
    response.raise_for_status()
    return response.json()


async def get_events(room_id: str) -> list:
    response = await _client.get(f"/events/{room_id}")
    response.raise_for_status()
    return response.json()
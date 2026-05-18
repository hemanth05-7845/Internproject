import httpx
import os

GIN_BASE_URL = os.getenv("GIN_EVENT_BASE_URL", "http://localhost:8081/api")


async def get_timer(room_id: str) -> dict:
    """Fetch timer snapshot from Gin event service."""
    async with httpx.AsyncClient() as client:
        response = await client.get(f"{GIN_BASE_URL}/timer/{room_id}", timeout=5.0)
        response.raise_for_status()
        return response.json()


async def get_events(room_id: str) -> list:
    """Fetch event feed from Gin event service."""
    async with httpx.AsyncClient() as client:
        response = await client.get(f"{GIN_BASE_URL}/events/{room_id}", timeout=5.0)
        response.raise_for_status()
        return response.json()

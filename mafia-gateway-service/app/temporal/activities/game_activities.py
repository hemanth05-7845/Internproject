from __future__ import annotations

import logging
import os

import httpx
from temporalio import activity

logger = logging.getLogger(__name__)

from app.services.spring_client.engine_client import (
    start_game as engine_start_game,
    get_game_state as engine_get_game_state,
    advance_phase as engine_advance_phase,
)
from app.services.gin_client.event_client import (
    start_phase_timer as gin_start_phase_timer,
    get_timer as gin_get_timer,
)

PHASE_DURATION_SECONDS: dict[str, int] = {
    "NIGHT":          30,
    "POLICE_GUESS":   30,
    "DOCTOR_SAVE":    30,
    "DAY_DISCUSSION": 60,
    "VOTING":         30,
    "SUNRISE":        15,
}


@activity.defn(name="start_game_activity")
async def start_game_activity(room_id: str) -> str:
    activity.logger.info("start_game_activity room_id=%s", room_id)
    await engine_start_game(room_id)
    snap = await engine_get_game_state(room_id)
    first_phase: str = snap.get("phase", "NIGHT")
    activity.logger.info("start_game_activity first_phase=%s room_id=%s", first_phase, room_id)
    return first_phase


@activity.defn(name="start_phase_timer_activity")
async def start_phase_timer_activity(room_id: str, phase: str) -> int:
    duration = PHASE_DURATION_SECONDS.get(phase, 0)
    if duration == 0:
        activity.logger.info("start_phase_timer_activity phase=%s has no timer, skipping room_id=%s",phase, room_id,)
        return 0
    activity.logger.info("start_phase_timer_activity room_id=%s phase=%s duration=%ds",room_id, phase, duration,)
    await gin_start_phase_timer(room_id, phase, duration)
    return duration


@activity.defn(name="advance_phase_activity")
async def advance_phase_activity(room_id: str) -> str:
    activity.logger.info("advance_phase_activity room_id=%s", room_id)
    await engine_advance_phase(room_id)
    snap = await engine_get_game_state(room_id)
    new_phase: str = snap.get("phase", "UNKNOWN")
    activity.logger.info("advance_phase_activity new_phase=%s room_id=%s", new_phase, room_id)
    return new_phase




@activity.defn(name="cleanup_room_activity")
async def cleanup_room_activity(room_id: str, reason: str) -> None:
    activity.logger.info("cleanup_room_activity room_id=%s reason=%s", room_id, reason)
    gin_base = os.getenv("GIN_EVENT_BASE_URL", "http://mafia-event-service:8081/api")
    try:
        async with httpx.AsyncClient(base_url=gin_base, timeout=5.0) as client:
            resp = await client.post(f"/api/phase/{room_id}/cancel")
            activity.logger.info("cleanup_room_activity cancelled timer room_id=%s status=%d",room_id, resp.status_code,)
    except Exception as exc:
        activity.logger.warning("cleanup_room_activity timer cancel failed room_id=%s: %s", room_id, exc)

from __future__ import annotations

import logging
from typing import Any

from temporalio.client import Client, WorkflowHandle
from temporalio.exceptions import WorkflowAlreadyStartedError

from app.temporal.models import (AbandonRoomSignal,GameEndedSignal,GameStartedSignal,PhaseAdvancedSignal,)
from app.temporal.worker.worker import TASK_QUEUE
from app.temporal.workflows.game_lifecycle import GameLifecycleWorkflow

logger = logging.getLogger(__name__)

def _workflow_id(room_id: str) -> str:
    return f"game-lifecycle-{room_id}"

def _handle(client: Client, room_id: str) -> WorkflowHandle:
    return client.get_workflow_handle(_workflow_id(room_id))


async def start_lifecycle_workflow(
    client: Client,
    room_id: str,
    room_code: str,
    host_username: str,
) -> None:
    try:
        await client.start_workflow(GameLifecycleWorkflow.run,args=[room_id, room_code, host_username],id=_workflow_id(room_id),task_queue=TASK_QUEUE,)
        logger.info("GameLifecycleWorkflow started room_id=%s wf_id=%s",room_id, _workflow_id(room_id),)
    except WorkflowAlreadyStartedError:
        logger.debug("GameLifecycleWorkflow already running for room_id=%s", room_id)


async def signal_game_started(
    client: Client,
    room_id: str,
    first_phase: str,
    host_username: str,
    room_code: str,
) -> None:
    await _handle(client, room_id).signal(
        GameLifecycleWorkflow.on_game_started,
        GameStartedSignal(
            first_phase=first_phase,
            host_username=host_username,
            room_code=room_code,
        ),
    )
    logger.info("Signalled game_started room_id=%s first_phase=%s", room_id, first_phase)


async def signal_phase_advanced(
    client: Client,
    room_id: str,
    new_phase: str,
) -> None:
    await _handle(client, room_id).signal(
        GameLifecycleWorkflow.on_phase_advanced,
        PhaseAdvancedSignal(new_phase=new_phase),
    )
    logger.info("Signalled phase_advanced room_id=%s new_phase=%s", room_id, new_phase)


async def signal_game_ended(client: Client,room_id: str,winner: str,) -> None:
    await _handle(client, room_id).signal(
        GameLifecycleWorkflow.on_game_ended,
        GameEndedSignal(winner=winner),
    )
    logger.info("Signalled game_ended room_id=%s winner=%s", room_id, winner)


async def signal_abandon_room(client: Client,room_id: str,reason: str = "players_dropped",) -> None:
    """Signal that the room should be cleaned up."""
    await _handle(client, room_id).signal(GameLifecycleWorkflow.on_abandon_room,AbandonRoomSignal(reason=reason),)
    logger.info("Signalled abandon_room room_id=%s reason=%s", room_id, reason)



async def query_room_state(client: Client, room_id: str) -> dict[str, Any]:
    try:
        result: dict = await _handle(client, room_id).query(GameLifecycleWorkflow.get_room_state,)
        return result
    except Exception as exc:
        logger.warning("query_room_state failed room_id=%s: %s", room_id, exc)
        return {}

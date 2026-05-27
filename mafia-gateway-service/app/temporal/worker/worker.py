from __future__ import annotations

import asyncio
import logging
import os

from temporalio.client import Client
from temporalio.worker import Worker

from app.temporal.workflows.game_lifecycle import GameLifecycleWorkflow
from app.temporal.activities.game_activities import (start_game_activity,start_phase_timer_activity,advance_phase_activity,cleanup_room_activity,)

logger = logging.getLogger(__name__)

TASK_QUEUE = "mafia-gateway"


async def create_temporal_client() -> Client:
    temporal_host = os.getenv("TEMPORAL_HOST", "temporal:7233")
    logger.info("Connecting to Temporal at %s", temporal_host)
    return await Client.connect(temporal_host)


async def run_worker(client: Client) -> None:
    worker = Worker(client,task_queue=TASK_QUEUE,
        workflows=[GameLifecycleWorkflow],
        activities=[start_game_activity,start_phase_timer_activity,advance_phase_activity,cleanup_room_activity,],
    )
    logger.info("Temporal worker starting on task_queue=%s", TASK_QUEUE)
    async with worker:
        await asyncio.Event().wait()

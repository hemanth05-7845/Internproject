import asyncio
import logging
import os
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.controllers.game_controller import router as game_router, set_temporal_client

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Startup: connect to Temporal, inject client into the controller,
             start the worker in a background task.
    Shutdown: cancel the worker task cleanly.
    """
    from app.temporal.worker.worker import create_temporal_client, run_worker

    temporal_client = await create_temporal_client()
    set_temporal_client(temporal_client)

    worker_task = asyncio.create_task(run_worker(temporal_client))
    logger.info("Temporal worker task started")

    yield  # FastAPI serves requests here

    logger.info("Shutting down Temporal worker...")
    worker_task.cancel()
    try:
        await worker_task
    except asyncio.CancelledError:
        pass
    logger.info("Temporal worker stopped")


app = FastAPI(title="Mafia Gateway Service", lifespan=lifespan)

ALLOWED_ORIGINS = os.getenv(
    "ALLOWED_ORIGINS",
    "http://localhost:5173"
).split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(game_router)

if __name__ == "__main__":
    port = int(os.getenv("PORT", os.getenv("GATEWAY_PORT", "8000")))
    uvicorn.run(app, host="0.0.0.0", port=port)

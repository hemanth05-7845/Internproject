import os
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.controllers.game_controller import router as game_router

app = FastAPI(title="Mafia Gateway Service")

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

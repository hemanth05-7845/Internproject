# Mafia Distributed Platform

This repository contains a distributed multiplayer Mafia game platform with a backend-authoritative architecture.

## Architecture

Frontend (React) -> FastAPI Gateway -> Spring Boot Game Engine + Gin Event Service -> MongoDB

## Services

- `mafia-engine-frontend`: React UI that polls game snapshots.
- `mafia-gateway-service`: FastAPI orchestration gateway and public API.
- `mafia-game-engine`: Spring Boot core game logic and state machine.
- `mafia-event-service`: Gin timer and asynchronous event handling.
- `shared-contracts`: JSON contract definitions shared across services.
- `database`: MongoDB schemas, seed data, and helper scripts.
- `docs`: architecture, API flow, sequence, and polling design docs.

## Quick Start

1. Copy `.env.example` to `.env` and adjust values if needed.
2. Start all services:
   - `docker compose up --build`
   - PowerShell: `./scripts/start-all-services.ps1`
3. Stop all services:
   - `docker compose down`
   - PowerShell: `./scripts/stop-all-services.ps1`

## Public API (Gateway)

- `POST /create-room`
- `POST /join-room`
- `POST /leave-room`
- `POST /start-game`
- `GET /game-state/{roomId}`
- `POST /send-message`
- `POST /submit-vote`
- `POST /player-action`

## Notes

- Frontend must only call the gateway service.
- Polling intervals are phase-sensitive and controlled by frontend logic.
- Backend state remains authoritative.

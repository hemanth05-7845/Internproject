import pytest
from fastapi.testclient import TestClient
from unittest.mock import AsyncMock, patch

from app.core.security import jwt_handler
from app.services.game_orchestrator import room_store
from main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def reset_store():
    room_store.SESSIONS.clear()
    room_store.CODE_TO_ID.clear()
    room_store.ROOM_HOSTS.clear()


def _get_token(user="user1", room="room-1", is_host=True):
    if is_host:
        room_store.cache_room("CODE", room, user)
    return jwt_handler.create_access_token(user, "dev-secret", "HS256", 60)


@patch("app.api.controllers.game_controller.engine_create_room", new_callable=AsyncMock)
def test_create_room(mock_create):
    mock_create.return_value = {"roomId": "room-1", "roomCode": "CODE", "hostUsername": "user1", "roomName": "Test", "playerCount": 1, "status": "ACTIVE"}
    token = _get_token()
    response = client.post(
        "/create-room",
        json={"room_name": "Test"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
    assert response.json()["room_id"] == "room-1"


@patch("app.api.controllers.game_controller.engine_join_by_code", new_callable=AsyncMock)
def test_join_room(mock_join):
    mock_join.return_value = {"roomId": "room-1", "roomCode": "CODE", "hostUsername": "host1", "roomName": "Test", "playerCount": 2, "status": "ACTIVE"}
    token = _get_token(user="user2", is_host=False)
    response = client.post(
        "/join-room",
        json={"room_code": "CODE01"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200


def test_health():
    assert client.get("/health").status_code == 200


@patch("app.api.controllers.game_controller.engine_start_game", new_callable=AsyncMock)
def test_start_game(mock_start):
    token = _get_token()
    mock_start.return_value = {"status": "started"}
    response = client.post("/start-game", json={"room_id": "CODE"}, headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200


@patch("app.api.controllers.game_controller.engine_advance_phase", new_callable=AsyncMock)
def test_advance_phase(mock_advance):
    token = _get_token()
    mock_advance.return_value = {"status": "advanced"}
    response = client.post("/advance-phase", json={"room_id": "room-1"}, headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200


@patch("app.api.controllers.game_controller.engine_resolve_voting", new_callable=AsyncMock)
def test_resolve_voting(mock_resolve):
    token = _get_token()
    mock_resolve.return_value = {"status": "resolved"}
    response = client.post("/resolve-voting", json={"room_id": "room-1"}, headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200


@patch("app.api.controllers.game_controller.engine_submit_vote", new_callable=AsyncMock)
def test_submit_vote(mock_vote):
    token = _get_token(user="voter")
    mock_vote.return_value = {"status": "voted"}
    response = client.post(
        "/submit-vote",
        json={"room_id": "room-1", "target_player": "target"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200


@patch("app.api.controllers.game_controller.engine_night_kill", new_callable=AsyncMock)
def test_submit_night_kill(mock_kill):
    token = _get_token()
    mock_kill.return_value = {"status": "killed"}
    response = client.post(
        "/night-kill",
        json={"room_id": "room-1", "target_player": "target"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200


@patch("app.api.controllers.game_controller.engine_police_guess", new_callable=AsyncMock)
def test_submit_police_guess(mock_guess):
    token = _get_token()
    mock_guess.return_value = {"status": "guessed"}
    response = client.post(
        "/police-guess",
        json={"room_id": "room-1", "suspect_player": "suspect"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200


@patch("app.api.controllers.game_controller.engine_send_message", new_callable=AsyncMock)
def test_send_message(mock_msg):
    token = _get_token(user="sender")
    mock_msg.return_value = {"status": "sent"}
    response = client.post(
        "/send-message",
        json={"room_id": "room-1", "message": "hello"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200


@patch("app.api.controllers.game_controller.engine_get_game_state", new_callable=AsyncMock)
@patch("app.api.controllers.game_controller.gin_get_timer", new_callable=AsyncMock)
def test_get_state(mock_timer, mock_state):
    token = _get_token()
    mock_state.return_value = {
        "phase": "NIGHT",
        "players": [{"name": "user1", "role": "MAFIA", "alive": True}],
        "alivePlayers": ["user1"],
        "eliminatedPlayers": [] ,
        "nightKillTarget": None,
        "policeGuessTarget": None,
        "policeGuessCorrect": None,
        "winner": "NONE",
        "chatMessages": [],
        "events": [],
        "allowedActions": [],
        "roomCode": "CODE",
        "hostUsername": "user1",
    }
    mock_timer.return_value = {"updatedAt": "2026-05-12T00:00:00Z"}
    response = client.get("/game-state/room-1", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200

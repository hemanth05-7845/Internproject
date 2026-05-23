import pytest
from fastapi.testclient import TestClient
from unittest.mock import AsyncMock, patch

from app.core.security import jwt_handler
from app.services.game_orchestrator import room_store
from main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def reset_store():
    room_store.reset()


def _token(user: str = "user1") -> str:
    return jwt_handler.create_access_token(user, "Hemanth-secret", "HS256", 60)


def _auth(user: str = "user1") -> dict:
    return {"Authorization": f"Bearer {_token(user)}"}


def _cache_room(
    room_code: str = "CODE01", room_id: str = "room-1", host: str = "user1"
) -> None:
    room_store.cache_room(room_code, room_id, host)


_ROOM_DICT = {
    "roomId": "room-1",
    "roomCode": "CODE01",
    "hostUsername": "user1",
    "roomName": "Test Room",
    "playerCount": 1,
    "status": "ACTIVE",
}

_GAME_STATE = {
    "phase": "NIGHT",
    "dayNumber": 1,
    "nightNumber": 1,
    "players": [{"name": "user1", "role": "MAFIA", "alive": True}],
    "alivePlayers": ["user1"],
    "eliminatedPlayers": [],
    "nightKillTarget": None,
    "nightKillFailed": None,
    "policeGuessTarget": None,
    "policeGuessCorrect": None,
    "winner": "NONE",
    "chatMessages": [],
    "events": [],
    "allowedActions": [],
    "roomCode": "CODE01",
    "hostUsername": "user1",
}


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok", "service": "mafia-gateway"}


def test_join_auth_returns_token():
    resp = client.post("/auth/join", json={"username": "alice"})
    assert resp.status_code == 200
    body = resp.json()
    assert "access_token" in body
    assert body["token_type"] == "bearer"


def test_join_auth_username_too_short():
    resp = client.post("/auth/join", json={"username": "a"})
    assert resp.status_code == 422


def test_missing_token_returns_401():
    resp = client.post("/create-room", json={"room_name": "Test"})
    assert resp.status_code == 401


def test_invalid_token_returns_401():
    resp = client.post(
        "/create-room",
        json={"room_name": "Test"},
        headers={"Authorization": "Bearer not-a-real-token"},
    )
    assert resp.status_code == 401


@patch("app.api.controllers.game_controller.engine_create_room", new_callable=AsyncMock)
def test_create_room_success(mock_create):
    mock_create.return_value = _ROOM_DICT
    resp = client.post("/create-room", json={"room_name": "Test Room"}, headers=_auth())
    assert resp.status_code == 200
    body = resp.json()
    assert body["room_id"] == "room-1"
    assert body["room_code"] == "CODE01"
    assert body["host_username"] == "user1"
    assert body["status"] == "ACTIVE"


@patch("app.api.controllers.game_controller.engine_create_room", new_callable=AsyncMock)
def test_create_room_engine_error_returns_500(mock_create):
    mock_create.side_effect = Exception("Spring is down")
    resp = client.post("/create-room", json={"room_name": "Test Room"}, headers=_auth())
    assert resp.status_code == 500
    assert "Spring is down" in resp.json()["detail"]


@patch(
    "app.api.controllers.game_controller.engine_join_by_code", new_callable=AsyncMock
)
def test_join_room_success(mock_join):
    mock_join.return_value = _ROOM_DICT
    resp = client.post(
        "/join-room", json={"room_code": "CODE01"}, headers=_auth("user2")
    )
    assert resp.status_code == 200
    assert resp.json()["room_id"] == "room-1"
    mock_join.assert_called_once_with("CODE01", "user2")


@patch(
    "app.api.controllers.game_controller.engine_join_by_code", new_callable=AsyncMock
)
def test_join_room_lowercse_code_is_uppercased(mock_join):
    mock_join.return_value = _ROOM_DICT
    resp = client.post(
        "/join-room", json={"room_code": "code01"}, headers=_auth("user2")
    )
    assert resp.status_code == 200
    mock_join.assert_called_once_with("CODE01", "user2")


@patch(
    "app.api.controllers.game_controller.engine_join_by_code", new_callable=AsyncMock
)
def test_join_room_not_found_returns_404(mock_join):
    mock_join.side_effect = Exception("Room not found")
    resp = client.post(
        "/join-room", json={"room_code": "NOPE00"}, headers=_auth("user2")
    )
    assert resp.status_code == 404
    assert "Room not found" in resp.json()["detail"]


@patch("app.api.controllers.game_controller.engine_get_players", new_callable=AsyncMock)
@patch("app.api.controllers.game_controller.engine_get_room", new_callable=AsyncMock)
def test_get_room_players_success(mock_room, mock_players):
    mock_players.return_value = [{"name": "user1"}, {"name": "user2"}]
    mock_room.return_value = {"hostUsername": "user1", "minPlayers": 6}
    resp = client.get("/room/CODE01/players", headers=_auth())
    assert resp.status_code == 200
    body = resp.json()
    assert body["playerCount"] == 2
    assert body["readyToStart"] is False
    assert body["hostUsername"] == "user1"


@patch("app.api.controllers.game_controller.engine_get_players", new_callable=AsyncMock)
@patch("app.api.controllers.game_controller.engine_get_room", new_callable=AsyncMock)
def test_get_room_players_ready_to_start(mock_room, mock_players):
    mock_players.return_value = [{"name": f"u{i}"} for i in range(6)]
    mock_room.return_value = {"hostUsername": "user1", "minPlayers": 6}
    resp = client.get("/room/CODE01/players", headers=_auth())
    assert resp.json()["readyToStart"] is True


@patch("app.api.controllers.game_controller.engine_get_players", new_callable=AsyncMock)
@patch("app.api.controllers.game_controller.engine_get_room", new_callable=AsyncMock)
def test_get_room_players_not_found_returns_404(mock_room, mock_players):
    mock_players.side_effect = Exception("not found")
    mock_room.return_value = {}
    resp = client.get("/room/NOPE00/players", headers=_auth())
    assert resp.status_code == 404


@patch("app.api.controllers.game_controller.engine_start_game", new_callable=AsyncMock)
def test_start_game_success(mock_start):
    _cache_room(room_id="room-1", host="user1")
    mock_start.return_value = {}
    resp = client.post(
        "/start-game", json={"room_id": "room-1"}, headers=_auth("user1")
    )
    assert resp.status_code == 200
    assert resp.json()["message"] == "Game started"


def test_start_game_room_not_cached_returns_404():
    resp = client.post(
        "/start-game", json={"room_id": "room-1"}, headers=_auth("user1")
    )
    assert resp.status_code == 404


def test_start_game_non_host_returns_403():
    _cache_room(room_id="room-1", host="user1")
    resp = client.post(
        "/start-game", json={"room_id": "room-1"}, headers=_auth("user2")
    )
    assert resp.status_code == 403
    assert resp.json()["detail"] == "Only the host can start the game"


@patch("app.api.controllers.game_controller.engine_start_game", new_callable=AsyncMock)
def test_start_game_engine_error_returns_500(mock_start):
    _cache_room(room_id="room-1", host="user1")
    mock_start.side_effect = Exception("engine crashed")
    resp = client.post(
        "/start-game", json={"room_id": "room-1"}, headers=_auth("user1")
    )
    assert resp.status_code == 500


@patch(
    "app.api.controllers.game_controller.engine_get_game_state", new_callable=AsyncMock
)
@patch("app.api.controllers.game_controller.gin_get_timer", new_callable=AsyncMock)
def test_get_game_state_success(mock_timer, mock_state):
    mock_state.return_value = _GAME_STATE
    mock_timer.return_value = {"updatedAt": "2026-05-12T00:00:00Z"}
    resp = client.get("/game-state/room-1", headers=_auth("user1"))
    assert resp.status_code == 200
    body = resp.json()
    assert body["phase"] == "NIGHT"
    assert body["my_role"] == "MAFIA"
    assert body["phase_ends_at"] == "2026-05-12T00:00:00Z"


@patch(
    "app.api.controllers.game_controller.engine_get_game_state", new_callable=AsyncMock
)
@patch("app.api.controllers.game_controller.gin_get_timer", new_callable=AsyncMock)
def test_get_game_state_role_hidden_for_other_players(mock_timer, mock_state):
    state = {
        **_GAME_STATE,
        "players": [
            {"name": "user1", "role": "MAFIA", "alive": True},
            {"name": "user2", "role": "VILLAGER", "alive": True},
        ],
    }
    mock_state.return_value = state
    mock_timer.return_value = {}
    resp = client.get("/game-state/room-1", headers=_auth("user2"))
    body = resp.json()
    players = {p["name"]: p for p in body["players"]}
    assert players["user2"]["role"] == "VILLAGER"
    assert players["user1"]["role"] is None


@patch(
    "app.api.controllers.game_controller.engine_get_game_state", new_callable=AsyncMock
)
@patch("app.api.controllers.game_controller.gin_get_timer", new_callable=AsyncMock)
def test_get_game_state_timer_failure_defaults_to_empty(mock_timer, mock_state):
    mock_state.return_value = _GAME_STATE
    mock_timer.side_effect = Exception("timer service down")
    resp = client.get("/game-state/room-1", headers=_auth("user1"))
    assert resp.status_code == 200
    assert resp.json()["phase_ends_at"] == ""


@patch(
    "app.api.controllers.game_controller.engine_get_game_state", new_callable=AsyncMock
)
def test_get_game_state_engine_error_returns_500(mock_state):
    mock_state.side_effect = Exception("backend down")
    resp = client.get("/game-state/room-1", headers=_auth())
    assert resp.status_code == 500


@patch(
    "app.api.controllers.game_controller.engine_advance_phase", new_callable=AsyncMock
)
def test_advance_phase_success(mock_advance):
    mock_advance.return_value = {}
    resp = client.post("/advance-phase", json={"room_id": "room-1"}, headers=_auth())
    assert resp.status_code == 200
    assert resp.json()["message"] == "Phase advanced"


@patch(
    "app.api.controllers.game_controller.engine_advance_phase", new_callable=AsyncMock
)
def test_advance_phase_engine_error_returns_500(mock_advance):
    mock_advance.side_effect = Exception("engine error")
    resp = client.post("/advance-phase", json={"room_id": "room-1"}, headers=_auth())
    assert resp.status_code == 500


@patch(
    "app.api.controllers.game_controller.engine_resolve_voting", new_callable=AsyncMock
)
def test_resolve_voting_success(mock_resolve):
    mock_resolve.return_value = {}
    resp = client.post("/resolve-voting", json={"room_id": "room-1"}, headers=_auth())
    assert resp.status_code == 200
    assert resp.json()["message"] == "Voting resolved"


@patch(
    "app.api.controllers.game_controller.engine_resolve_voting", new_callable=AsyncMock
)
def test_resolve_voting_engine_error_returns_500(mock_resolve):
    mock_resolve.side_effect = Exception("engine error")
    resp = client.post("/resolve-voting", json={"room_id": "room-1"}, headers=_auth())
    assert resp.status_code == 500


@patch("app.api.controllers.game_controller.engine_night_kill", new_callable=AsyncMock)
def test_night_kill_success(mock_kill):
    mock_kill.return_value = {}
    resp = client.post(
        "/night-kill",
        json={"room_id": "room-1", "target_player": "victim"},
        headers=_auth(),
    )
    assert resp.status_code == 200
    assert resp.json()["message"] == "Night kill recorded"
    mock_kill.assert_called_once_with("room-1", "victim")


@patch("app.api.controllers.game_controller.engine_night_kill", new_callable=AsyncMock)
def test_night_kill_engine_error_returns_500(mock_kill):
    mock_kill.side_effect = Exception("engine error")
    resp = client.post(
        "/night-kill",
        json={"room_id": "room-1", "target_player": "victim"},
        headers=_auth(),
    )
    assert resp.status_code == 500


@patch(
    "app.api.controllers.game_controller.engine_police_guess", new_callable=AsyncMock
)
def test_police_guess_success(mock_guess):
    mock_guess.return_value = {}
    resp = client.post(
        "/police-guess",
        json={"room_id": "room-1", "suspect_player": "suspect"},
        headers=_auth(),
    )
    assert resp.status_code == 200
    assert resp.json()["message"] == "Police guess recorded"
    mock_guess.assert_called_once_with("room-1", "suspect")


@patch(
    "app.api.controllers.game_controller.engine_police_guess", new_callable=AsyncMock
)
def test_police_guess_engine_error_returns_500(mock_guess):
    mock_guess.side_effect = Exception("engine error")
    resp = client.post(
        "/police-guess",
        json={"room_id": "room-1", "suspect_player": "suspect"},
        headers=_auth(),
    )
    assert resp.status_code == 500


@patch("app.api.controllers.game_controller.engine_doctor_save", new_callable=AsyncMock)
def test_doctor_save_success(mock_save):
    mock_save.return_value = {}
    resp = client.post(
        "/doctor-save",
        json={"room_id": "room-1", "saved_player": "patient"},
        headers=_auth(),
    )
    assert resp.status_code == 200
    assert resp.json()["message"] == "Doctor save recorded"
    mock_save.assert_called_once_with("room-1", "patient")


@patch("app.api.controllers.game_controller.engine_doctor_save", new_callable=AsyncMock)
def test_doctor_save_engine_error_returns_500(mock_save):
    mock_save.side_effect = Exception("engine error")
    resp = client.post(
        "/doctor-save",
        json={"room_id": "room-1", "saved_player": "patient"},
        headers=_auth(),
    )
    assert resp.status_code == 500


@patch("app.api.controllers.game_controller.engine_submit_vote", new_callable=AsyncMock)
def test_submit_vote_success(mock_vote):
    mock_vote.return_value = {}
    resp = client.post(
        "/submit-vote",
        json={"room_id": "room-1", "target_player": "target"},
        headers=_auth("voter"),
    )
    assert resp.status_code == 200
    assert resp.json()["message"] == "Vote submitted"
    mock_vote.assert_called_once_with("room-1", "voter", "target")


@patch("app.api.controllers.game_controller.engine_submit_vote", new_callable=AsyncMock)
def test_submit_vote_engine_error_returns_500(mock_vote):
    mock_vote.side_effect = Exception("engine error")
    resp = client.post(
        "/submit-vote",
        json={"room_id": "room-1", "target_player": "target"},
        headers=_auth("voter"),
    )
    assert resp.status_code == 500


@patch(
    "app.api.controllers.game_controller.engine_send_message", new_callable=AsyncMock
)
def test_send_message_success(mock_msg):
    mock_msg.return_value = {}
    resp = client.post(
        "/send-message",
        json={"room_id": "room-1", "message": "hello"},
        headers=_auth("sender"),
    )
    assert resp.status_code == 200
    assert resp.json()["message"] == "Message sent"
    mock_msg.assert_called_once_with("room-1", "sender", "hello")


@patch(
    "app.api.controllers.game_controller.engine_send_message", new_callable=AsyncMock
)
def test_send_message_engine_error_returns_500(mock_msg):
    mock_msg.side_effect = Exception("engine error")
    resp = client.post(
        "/send-message",
        json={"room_id": "room-1", "message": "hello"},
        headers=_auth("sender"),
    )
    assert resp.status_code == 500

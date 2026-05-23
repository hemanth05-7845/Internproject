import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from app.services.spring_client import engine_client

def _mock_response(payload):
    resp = MagicMock()
    resp.raise_for_status = MagicMock()
    resp.json = MagicMock(return_value=payload)
    return resp


@pytest.fixture
def mock_client(monkeypatch):

    client = MagicMock()
    client.get = AsyncMock()
    client.post = AsyncMock()

    monkeypatch.setattr(engine_client, "_client", client)
    return client


@pytest.mark.asyncio
async def test_get_room_by_code(mock_client):

    mock_client.get.return_value = _mock_response({"roomId": "room-1"})

    res = await engine_client.get_room_by_code("CODE")

    assert res["roomId"] == "room-1"
    mock_client.get.assert_awaited_once_with("/rooms/by-code/CODE")


@pytest.mark.asyncio
async def test_get_players_by_code(mock_client):

    mock_client.get.return_value = _mock_response([{"username": "p1"}])

    res = await engine_client.get_players_by_code("CODE")

    assert res[0]["username"] == "p1"
    mock_client.get.assert_awaited_once_with("/rooms/by-code/CODE/players")


@pytest.mark.asyncio
async def test_get_game_state(mock_client):

    mock_client.get.return_value = _mock_response({"phase": "DAY"})

    res = await engine_client.get_game_state("r1")

    assert res["phase"] == "DAY"
    mock_client.get.assert_awaited_once_with("/game-state/r1")


@pytest.mark.asyncio
async def test_create_room(mock_client):

    mock_client.post.return_value = _mock_response(
        {"roomId": "room-1", "hostUsername": "host1"}
    )

    res = await engine_client.create_room("My Room", "host1")

    assert res["roomId"] == "room-1"
    mock_client.post.assert_awaited_once_with(
        "/rooms/create",
        json={"roomName": "My Room", "hostUsername": "host1"},
    )


@pytest.mark.asyncio
async def test_join_room_by_code(mock_client):

    mock_client.post.return_value = _mock_response({"status": "ok"})

    await engine_client.join_room_by_code("CODE", "p1")

    mock_client.post.assert_awaited_once_with(
        "/rooms/join-by-code",
        json={"roomCode": "CODE", "username": "p1"},
    )


@pytest.mark.asyncio
async def test_start_game(mock_client):

    mock_client.post.return_value = _mock_response({"status": "started"})

    await engine_client.start_game("r1")

    mock_client.post.assert_awaited_once_with("/game-state/r1/start", json={})


@pytest.mark.asyncio
async def test_advance_phase(mock_client):

    mock_client.post.return_value = _mock_response({"phase": "NIGHT"})

    await engine_client.advance_phase("r1")

    mock_client.post.assert_awaited_once_with("/game/r1/advance-phase", json={})


@pytest.mark.asyncio
async def test_resolve_voting(mock_client):

    mock_client.post.return_value = _mock_response({"eliminated": "p2"})

    await engine_client.resolve_voting("r1")

    mock_client.post.assert_awaited_once_with("/game/r1/resolve-voting", json={})


@pytest.mark.asyncio
async def test_submit_night_kill(mock_client):

    mock_client.post.return_value = _mock_response({"status": "ok"})

    await engine_client.submit_night_kill("r1", "p2")

    mock_client.post.assert_awaited_once_with(
        "/game/r1/submit-night-kill",
        json={"targetPlayer": "p2"},
    )


@pytest.mark.asyncio
async def test_submit_police_guess(mock_client):

    mock_client.post.return_value = _mock_response({"isMafia": True})

    await engine_client.submit_police_guess("r1", "p2")

    mock_client.post.assert_awaited_once_with(
        "/game/r1/submit-police-guess",
        json={"suspectPlayer": "p2"},
    )


@pytest.mark.asyncio
async def test_submit_doctor_save(mock_client):

    mock_client.post.return_value = _mock_response({"status": "ok"})

    await engine_client.submit_doctor_save("r1", "p3")

    mock_client.post.assert_awaited_once_with(
        "/game/r1/submit-doctor-save",
        json={"savedPlayer": "p3"},
    )


@pytest.mark.asyncio
async def test_submit_vote(mock_client):

    mock_client.post.return_value = _mock_response({"status": "ok"})

    await engine_client.submit_vote("r1", "voter1", "target1")

    mock_client.post.assert_awaited_once_with(
        "/rooms/r1/vote",
        json={"voterId": "voter1", "targetPlayerId": "target1"},
    )


@pytest.mark.asyncio
async def test_send_message(mock_client):

    mock_client.post.return_value = _mock_response({"messageId": "m1"})

    await engine_client.send_message("r1", "alice", "hello")

    mock_client.post.assert_awaited_once_with(
        "/rooms/r1/message",
        json={"senderUsername": "alice", "content": "hello"},
    )


@pytest.mark.asyncio
async def test_raise_for_status_called_on_get(mock_client):


    resp = _mock_response({"roomId": "x"})
    mock_client.get.return_value = resp

    await engine_client.get_room_by_code("X")

    resp.raise_for_status.assert_called_once()


@pytest.mark.asyncio
async def test_raise_for_status_called_on_post(mock_client):

    resp = _mock_response({})
    mock_client.post.return_value = resp

    await engine_client.start_game("r1")

    resp.raise_for_status.assert_called_once()

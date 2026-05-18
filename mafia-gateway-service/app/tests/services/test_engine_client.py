import pytest
from unittest.mock import AsyncMock, Mock, patch
from app.services.spring_client import engine_client

@pytest.fixture
def mock_httpx():
    with patch("app.services.spring_client.engine_client.httpx.AsyncClient") as mock_client:
        mock_instance = AsyncMock()
        mock_client.return_value.__aenter__.return_value = mock_instance
        yield mock_instance

@pytest.mark.asyncio
async def test_get_room_by_code(mock_httpx):
    mock_resp = Mock()
    mock_resp.raise_for_status = Mock()
    mock_resp.json = Mock(return_value={"roomId": "room-1"})
    mock_httpx.get.return_value = mock_resp

    res = await engine_client.get_room_by_code("CODE")
    assert res["roomId"] == "room-1"
    mock_httpx.get.assert_called_once_with(f"{engine_client.SPRING_BASE_URL}/rooms/by-code/CODE", timeout=5.0)

@pytest.mark.asyncio
async def test_create_room(mock_httpx):
    mock_resp = Mock()
    mock_resp.raise_for_status = Mock()
    mock_resp.json = Mock(return_value={"roomId": "room-1", "hostUsername": "host1"})
    mock_httpx.post.return_value = mock_resp

    res = await engine_client.create_room("My Room", "host1")
    assert res["roomId"] == "room-1"
    mock_httpx.post.assert_called_once_with(
        f"{engine_client.SPRING_BASE_URL}/rooms/create",
        json={"roomName": "My Room", "hostUsername": "host1"},
        timeout=5.0
    )

@pytest.mark.asyncio
async def test_all_other_engine_calls(mock_httpx):
    mock_resp = Mock()
    mock_resp.raise_for_status = Mock()
    mock_resp.json = Mock(return_value={"status": "ok"})
    mock_httpx.post.return_value = mock_resp
    mock_httpx.get.return_value = mock_resp

    await engine_client.join_room_by_code("CODE", "p1")
    await engine_client.get_players_by_code("CODE")
    await engine_client.start_game("r1")
    await engine_client.get_game_state("r1")
    await engine_client.advance_phase("r1")
    await engine_client.resolve_voting("r1")
    await engine_client.submit_night_kill("r1", "p1")
    await engine_client.submit_police_guess("r1", "p1")
    await engine_client.submit_vote("r1", "voter", "target")
    await engine_client.send_message("r1", "voter", "msg")

    assert mock_httpx.post.call_count == 8
    assert mock_httpx.get.call_count == 2

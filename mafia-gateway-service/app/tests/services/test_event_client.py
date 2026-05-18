import pytest
from unittest.mock import AsyncMock, Mock, patch
from app.services.gin_client import event_client

@pytest.fixture
def mock_httpx():
    with patch("app.services.gin_client.event_client.httpx.AsyncClient") as mock_client:
        mock_instance = AsyncMock()
        mock_client.return_value.__aenter__.return_value = mock_instance
        yield mock_instance

@pytest.mark.asyncio
async def test_get_timer(mock_httpx):
    mock_resp = Mock()
    mock_resp.raise_for_status = Mock()
    mock_resp.json = Mock(return_value={"phase": "NIGHT"})
    mock_httpx.get.return_value = mock_resp

    res = await event_client.get_timer("room1")
    assert res["phase"] == "NIGHT"

@pytest.mark.asyncio
async def test_get_events(mock_httpx):
    mock_resp = Mock()
    mock_resp.raise_for_status = Mock()
    mock_resp.json = Mock(return_value=[{"event": "KILL"}])
    mock_httpx.get.return_value = mock_resp

    res = await event_client.get_events("room1")
    assert len(res) == 1

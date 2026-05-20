import pytest
from unittest.mock import AsyncMock, MagicMock


def _mock_response(payload):
    resp = MagicMock()
    resp.raise_for_status = MagicMock()
    resp.json = MagicMock(return_value=payload)
    return resp


@pytest.fixture
def mock_client(monkeypatch):
    from app.services.gin_client import event_client

    client = MagicMock()
    client.get = AsyncMock()

    monkeypatch.setattr(event_client, "_client", client)
    return client


@pytest.mark.asyncio
async def test_get_timer(mock_client):
    from app.services.gin_client import event_client

    mock_client.get.return_value = _mock_response({"phase": "NIGHT"})

    res = await event_client.get_timer("room1")

    assert res["phase"] == "NIGHT"
    mock_client.get.assert_awaited_once_with("/timer/room1")


@pytest.mark.asyncio
async def test_get_events(mock_client):
    from app.services.gin_client import event_client

    mock_client.get.return_value = _mock_response([{"event": "KILL"}])

    res = await event_client.get_events("room1")

    assert len(res) == 1
    mock_client.get.assert_awaited_once_with("/events/room1")


@pytest.mark.asyncio
async def test_raise_for_status_called_on_get_timer(mock_client):
    from app.services.gin_client import event_client

    resp = _mock_response({"phase": "DAY"})
    mock_client.get.return_value = resp

    await event_client.get_timer("room2")

    resp.raise_for_status.assert_called_once()

import pytest
from app.services.game_orchestrator import room_store

@pytest.fixture(autouse=True)
def reset_store():
    room_store.SESSIONS.clear()
    room_store.CODE_TO_ID.clear()
    room_store.ROOM_HOSTS.clear()

def test_register_session():
    assert room_store.session_exists("user1") is False
    room_store.register_session("user1")
    assert room_store.session_exists("user1") is True

def test_cache_and_get_room():
    room_store.cache_room("CODE1", "room-1", "host1")
    
    assert room_store.get_room_id("code1") == "room-1"
    assert room_store.get_room_id("CODE1") == "room-1"
    assert room_store.get_host("room-1") == "host1"

def test_is_host():
    room_store.cache_room("CODE1", "room-1", "host1")
    assert room_store.is_host("room-1", "host1") is True
    assert room_store.is_host("room-1", "player1") is False
    assert room_store.is_host("unknown-room", "host1") is False

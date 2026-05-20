import pytest

from app.services.game_orchestrator import room_store


@pytest.fixture(autouse=True)
def reset_store():
    room_store._SESSIONS.clear()
    room_store._CODE_TO_ID.clear()
    room_store._ROOM_HOSTS.clear()


def test_register_session_marks_user_present():
    assert room_store._SESSIONS.get("user1") is None

    room_store.register_session("user1")

    assert room_store._SESSIONS.get("user1") is True


def test_cache_and_get_room_case_insensitive_code():
    room_store.cache_room("CODE1", "room-1", "host1")

    assert room_store.get_room_id("code1") == "room-1"
    assert room_store.get_room_id("CODE1") == "room-1"
    assert room_store.get_host("room-1") == "host1"


def test_get_room_id_returns_none_for_unknown_code():
    assert room_store.get_room_id("missing") is None


def test_get_host_returns_none_for_unknown_room():
    assert room_store.get_host("missing-room") is None


def test_is_host_checks_room_owner():
    room_store.cache_room("CODE1", "room-1", "host1")

    assert room_store.is_host("room-1", "host1") is True
    assert room_store.is_host("room-1", "player1") is False
    assert room_store.is_host("unknown-room", "host1") is False

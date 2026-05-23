import { API_BASE_URL } from "../../config/api";

const auth = (token) => ({ "Content-Type": "application/json", Authorization: `Bearer ${token}` });

const parseErrorMessage = async (res) => {
  const text = await res.text();
  if (!text) return "Request failed";
  try {
    const data = JSON.parse(text);
    return data.detail || data.message || text;
  } catch {
    return text;
  }
};

export async function createRoom(token, roomName) {
  const res = await fetch(`${API_BASE_URL}/create-room`, {
    method: "POST", headers: auth(token),
    body: JSON.stringify({ room_name: roomName }),
  });
  if (!res.ok) throw new Error(await parseErrorMessage(res));
  return res.json(); // { room_id, room_code, host_username, ... }
}

export async function joinRoom(token, roomCode) {
  const res = await fetch(`${API_BASE_URL}/join-room`, {
    method: "POST", headers: auth(token),
    body: JSON.stringify({ room_code: roomCode.toUpperCase() }),
  });
  if (!res.ok) {
    const msg = await parseErrorMessage(res);
    if (/room is not active|in_game|game started/i.test(msg)) {
      throw new Error("Game already started");
    }
    if (/room not found/i.test(msg)) {
      throw new Error("Room not found");
    }
    throw new Error(msg || "Failed to join room");
  }
  return res.json();
}

export async function getRoomPlayers(token, roomCode) {
  const res = await fetch(`${API_BASE_URL}/room/${roomCode}/players`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(await parseErrorMessage(res));
  return res.json(); // { players, playerCount, minPlayers, readyToStart, hostUsername }
}

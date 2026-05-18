import { API_BASE_URL } from "../../config/api";

const auth = (token) => ({ "Content-Type": "application/json", Authorization: `Bearer ${token}` });

export async function createRoom(token, roomName) {
  const res = await fetch(`${API_BASE_URL}/create-room`, {
    method: "POST", headers: auth(token),
    body: JSON.stringify({ room_name: roomName }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json(); // { room_id, room_code, host_username, ... }
}

export async function joinRoom(token, roomCode) {
  const res = await fetch(`${API_BASE_URL}/join-room`, {
    method: "POST", headers: auth(token),
    body: JSON.stringify({ room_code: roomCode.toUpperCase() }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function getRoomPlayers(token, roomCode) {
  const res = await fetch(`${API_BASE_URL}/room/${roomCode}/players`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json(); // { players, playerCount, minPlayers, readyToStart, hostUsername }
}

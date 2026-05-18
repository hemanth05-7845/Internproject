import { API_BASE_URL } from "../../config/api";

const auth = (token) => ({ "Content-Type": "application/json", Authorization: `Bearer ${token}` });

export async function getGameState(token, roomId) {
  const res = await fetch(`${API_BASE_URL}/game-state/${roomId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error("Failed to fetch game state");
  return res.json();
}

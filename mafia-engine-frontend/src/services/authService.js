import { API_BASE_URL } from "../config/api";

/** Name-only auth — no password needed */
export async function join(username) {
  const res = await fetch(`${API_BASE_URL}/auth/join`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username }),
  });
  if (!res.ok) throw new Error("Failed to join");
  return res.json(); // { access_token, token_type }
}

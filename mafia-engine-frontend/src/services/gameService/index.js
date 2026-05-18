import { API_BASE_URL } from "../../config/api";

const auth = (token) => ({ "Content-Type": "application/json", Authorization: `Bearer ${token}` });

const post = async (token, path, body) => {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST", headers: auth(token), body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
};

export const startGame    = (token, roomId) => post(token, "/start-game",     { room_id: roomId });
export const advancePhase = (token, roomId) => post(token, "/advance-phase",  { room_id: roomId });
export const resolveVoting= (token, roomId) => post(token, "/resolve-voting", { room_id: roomId });

export const submitVote   = (token, roomId, targetPlayer) =>
  post(token, "/submit-vote",   { room_id: roomId, target_player: targetPlayer });

export const submitNightKill = (token, roomId, targetPlayer) =>
  post(token, "/night-kill",    { room_id: roomId, target_player: targetPlayer });

export const submitPoliceGuess = (token, roomId, suspectPlayer) =>
  post(token, "/police-guess",  { room_id: roomId, suspect_player: suspectPlayer });

export const submitDoctorSave = (token, roomId, savedPlayer) =>
  post(token, "/doctor-save",   { room_id: roomId, saved_player: savedPlayer });

export const sendMessage = (token, roomId, message) =>
  post(token, "/send-message",  { room_id: roomId, message });


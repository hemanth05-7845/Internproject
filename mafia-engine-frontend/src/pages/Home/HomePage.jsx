import { useState, useCallback } from "react";
import { useAuth } from "../../context/AuthContext";
import { join } from "../../services/authService";
import { createRoom, joinRoom } from "../../services/roomService";
import "./HomePage.css";

export default function HomePage({ onEnterLobby }) {
  const { setToken, setUsername } = useAuth();
  const [name, setName]       = useState("");
  const [code, setCode]       = useState("");
  const [mode, setMode]       = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState("");

  const getToken = async (n) => {
    const { access_token } = await join(n);
    setToken(access_token);
    setUsername(n);
    return access_token;
  };

  const handleCreate = useCallback(async () => {
    if (!name.trim()) return setError("Enter your name first");
    setLoading(true); setError("");
    try {
      const token = await getToken(name.trim());
      const room  = await createRoom(token, `${name.trim()}'s Room`);
      onEnterLobby(room);
    } catch (e) { setError(e.message || "Failed to create room"); }
    finally { setLoading(false); }
  }, [name]);

  const handleJoin = useCallback(async () => {
    if (!name.trim()) return setError("Enter your name first");
    if (code.trim().length !== 6) return setError("Enter a valid 6-letter room code");
    setLoading(true); setError("");
    try {
      const token = await getToken(name.trim());
      const room  = await joinRoom(token, code.trim().toUpperCase());
      onEnterLobby(room);
    } catch (e) { setError(e.message || "Room not found"); }
    finally { setLoading(false); }
  }, [name, code]);

  return (
    <div className="home-page">
      <div className="home-card glass-lg fade-up">
        <div className="home-logo">
          <h1 className="logo-title">MAFIA</h1>
          <span className="logo-accent" />
          <p className="logo-sub">Who among you can be trusted?</p>
        </div>

        <div className="home-form">
          <div className="form-field">
            <label className="field-label">Your Name</label>
            <input
              id="player-name"
              className="input"
              placeholder="Enter your display name…"
              value={name}
              maxLength={20}
              onChange={(e) => { setName(e.target.value); setError(""); }}
              onKeyDown={(e) => e.key === "Enter" && mode === "create" && handleCreate()}
              disabled={loading}
              autoFocus
            />
          </div>

          {!mode && (
            <div className="home-actions">
              <button id="btn-create" className="btn btn-primary btn-lg btn-full"
                onClick={() => setMode("create")}>
                Create Room
              </button>
              <button id="btn-join-toggle" className="btn btn-secondary btn-lg btn-full"
                onClick={() => setMode("join")}>
                Join Room
              </button>
            </div>
          )}

          {mode === "create" && (
            <div className="home-actions fade-up">
              <div className="info-box">
                A 6-letter code will be generated — share it with your friends.
              </div>
              <button id="btn-create-confirm" className="btn btn-primary btn-lg btn-full"
                onClick={handleCreate} disabled={loading || !name.trim()}>
                {loading ? "Creating…" : "Start Room"}
              </button>
              <button className="btn btn-secondary btn-full"
                onClick={() => { setMode(null); setError(""); }} disabled={loading}>
                Back
              </button>
            </div>
          )}

          {mode === "join" && (
            <div className="home-actions fade-up">
              <div className="form-field">
                <label className="field-label">Room Code</label>
                <input
                  id="room-code-input"
                  className="input code-input"
                  placeholder="XXXXXX"
                  value={code}
                  maxLength={6}
                  onChange={(e) => { setCode(e.target.value.toUpperCase()); setError(""); }}
                  onKeyDown={(e) => e.key === "Enter" && handleJoin()}
                  disabled={loading}
                />
              </div>
              <button id="btn-join-confirm" className="btn btn-primary btn-lg btn-full"
                onClick={handleJoin} disabled={loading || !name.trim() || code.length !== 6}>
                {loading ? "Joining…" : "Join Game"}
              </button>
              <button className="btn btn-secondary btn-full"
                onClick={() => { setMode(null); setError(""); }} disabled={loading}>
                Back
              </button>
            </div>
          )}

          {error && <div className="error-msg">{error}</div>}
        </div>

        {/* <div className="home-roles">
          <div className="role-pill mafia-pill">Mafia</div>
          <div className="role-pill police-pill">Police</div>
          <div className="role-pill villager-pill">Villager</div>
        </div> */}
      </div>
    </div>
  );
}

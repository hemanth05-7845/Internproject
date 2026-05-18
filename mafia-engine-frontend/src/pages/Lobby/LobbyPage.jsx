import { useState, useCallback } from "react";
import { useAuth } from "../../context/AuthContext";
import { usePolling } from "../../hooks/usePolling";
import { getRoomPlayers } from "../../services/roomService";
import { startGame } from "../../services/gameService";
import "./LobbyPage.css";

export default function LobbyPage({ roomId, roomCode, hostUsername, onEnterGame }) {
  const { token, username } = useAuth();
  const [players, setPlayers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState("");
  const [copied, setCopied]   = useState(false);

  const isHost     = username === hostUsername;
  const minPlayers = 2;
  const canStart   = isHost && players.length >= minPlayers;

  const fetchPlayers = useCallback(async () => {
    if (!token || !roomCode) return;
    try {
      const data = await getRoomPlayers(token, roomCode);
      setPlayers(data.players || []);
    } catch { /* retry */ }
  }, [token, roomCode]);

  usePolling(fetchPlayers, 2000, true);

  const handleCopy = () => {
    navigator.clipboard.writeText(roomCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleStart = useCallback(async () => {
    if (!canStart) return;
    setLoading(true); setError("");
    try {
      await startGame(token, roomId);
      onEnterGame();
    } catch (e) {
      setError(e.message || "Failed to start game");
    } finally {
      setLoading(false);
    }
  }, [canStart, token, roomId, onEnterGame]);

  return (
    <div className="lobby-page">
      <div className="lobby-layout fade-up">
        {/* Room code card */}
        <div className="code-card glass-lg">
          <p className="code-label">Room Code</p>
          <div className="code-display">{roomCode}</div>
          <button id="btn-copy-code" className="btn btn-secondary" onClick={handleCopy}>
            {copied ? "Copied" : "Copy Code"}
          </button>
        </div>

        {/* Players card */}
        <div className="players-card glass">
          <div className="players-header">
            <h2>Players</h2>
            <span className={`count-badge ${players.length >= minPlayers ? "ready" : ""}`}>
              {players.length} / {minPlayers}
            </span>
          </div>

          <div className="players-grid">
            {players.map((p, i) => (
              <div key={i} className={`player-slot ${p.name === username ? "me" : ""}`}>
                <span className="slot-avatar">{p.name?.[0]?.toUpperCase() || "?"}</span>
                <span className="slot-name">{p.name}</span>
                {p.name === hostUsername && <span className="host-label">HOST</span>}
                {p.name === username    && <span className="you-tag">You</span>}
              </div>
            ))}
            {Array.from({ length: Math.max(0, minPlayers - players.length) }).map((_, i) => (
              <div key={`empty-${i}`} className="player-slot empty">
                <span className="slot-avatar empty-avatar">?</span>
                <span className="slot-name">Waiting…</span>
              </div>
            ))}
          </div>

          {players.length < minPlayers && (
            <div className="waiting-msg">
              Need {minPlayers - players.length} more player{minPlayers - players.length !== 1 ? "s" : ""} to start
            </div>
          )}

          {error && <div className="error-msg">{error}</div>}

          {isHost ? (
            <button id="btn-start-game" className="btn btn-primary btn-lg btn-full"
              onClick={handleStart} disabled={!canStart || loading}>
              {loading ? "Starting…" : canStart ? "Start Game" : `Need ${minPlayers} players`}
            </button>
          ) : (
            <div className="waiting-msg host-waiting">
              Waiting for <strong>{hostUsername}</strong> to start
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

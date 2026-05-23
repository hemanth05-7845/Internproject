import { useCallback, useEffect, useState, useRef } from "react";
import { useAuth } from "./context/AuthContext";
import { useGameSocket } from "./hooks/useGameSocket";
import HomePage from "./pages/Home/HomePage";
import LobbyPage from "./pages/Lobby/LobbyPage";
import GameRoomPage from "./pages/GameRoom/GameRoomPage";
import ResultsPage from "./pages/Results/ResultsPage";

export default function App() {
  const { token } = useAuth();

  const [view, setView]                 = useState("HOME");
  const [roomId,       setRoomId]       = useState(null);
  const [roomCode,     setRoomCode]     = useState(null);
  const [hostUsername, setHostUsername] = useState(null);
  const [snapshot,     setSnapshot]     = useState(null);

  /**
   * activeRef is the source-of-truth for "should we process WS snapshots?"
   * Set synchronously in handleEnterLobby / handleRestart so any WS frame
   * that arrives between click and React re-render is silently dropped.
   */
  const activeRef = useRef(false);

  const handleSnapshot = useCallback((data) => {
    if (!activeRef.current) return; // ignore all frames when HOME or RESULTS
    setSnapshot(data);
    setView((current) => {
      if (current === "LOBBY" && data.phase && data.phase !== "LOBBY") return "GAME";
      if (current === "GAME"  && data.phase === "GAME_OVER")           return "RESULTS";
      return current;
    });
  }, []);

  // Connect WS as soon as roomId is known (lobby + game)
  useGameSocket(token, roomId, handleSnapshot, !!roomId);

  const handleEnterLobby = useCallback((info) => {
    activeRef.current = true;          // start accepting snapshots
    setRoomId(info.room_id);
    setRoomCode(info.room_code);
    setHostUsername(info.host_username);
    setView("LOBBY");
    localStorage.setItem("mafia_room_id", info.room_id);
    localStorage.setItem("mafia_room_code", info.room_code);
    localStorage.setItem("mafia_host_username", info.host_username);
  }, []);

  const handleEnterGame = useCallback(() => {
    setSnapshot(null);
    setView("GAME");
  }, []);

  const handleRestart = useCallback(() => {
    // Full page reload: kills all WebSocket connections and React state.
    // localStorage keeps the token so user stays "logged in".
    localStorage.removeItem("mafia_room_id");
    localStorage.removeItem("mafia_room_code");
    localStorage.removeItem("mafia_host_username");
    window.location.reload();
  }, []);

  useEffect(() => {
    if (!token || roomId) return;
    const storedRoomId = localStorage.getItem("mafia_room_id") || "";
    if (!storedRoomId) return;
    activeRef.current = true;
    setRoomId(storedRoomId);
    setRoomCode(localStorage.getItem("mafia_room_code"));
    setHostUsername(localStorage.getItem("mafia_host_username"));
    setView("LOBBY");
  }, [token, roomId]);

  return (
    <main>
      {view === "HOME"    && <HomePage    onEnterLobby={handleEnterLobby} />}
      {view === "LOBBY"   && <LobbyPage   roomId={roomId} roomCode={roomCode}
                                          hostUsername={hostUsername} onEnterGame={handleEnterGame} />}
      {view === "GAME"    && <GameRoomPage roomId={roomId} roomCode={roomCode}
                                          snapshot={snapshot} hostUsername={hostUsername} />}
      {view === "RESULTS" && <ResultsPage  snapshot={snapshot} onRestart={handleRestart} />}
    </main>
  );
}

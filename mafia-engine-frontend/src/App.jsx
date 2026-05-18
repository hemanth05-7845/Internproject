import { useCallback, useState, useRef } from "react";
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
  }, []);

  const handleEnterGame = useCallback(() => {
    setSnapshot(null);
    setView("GAME");
  }, []);

  const handleRestart = useCallback(() => {
    // Full page reload: kills all WebSocket connections and React state.
    // localStorage keeps the token so user stays "logged in".
    window.location.reload();
  }, []);

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

import { useEffect, useRef } from "react";

const WS_BASE = (import.meta.env.VITE_GATEWAY_BASE_URL || "http://localhost:8000")
  .replace(/^http/, "ws");

/**
 * Opens a WebSocket to ws://<gateway>/ws/<roomId>?token=<jwt>
 * and calls onSnapshot(data) every time the server pushes an update.
 * Auto-reconnects on disconnect, but STOPS when disabled or roomId clears.
 *
 * Key design: each useEffect run owns a `cancelled` flag.
 * When deps change (roomId null'd on restart), cleanup sets cancelled=true
 * so the pending onclose handler never schedules a stale reconnect.
 */
export function useGameSocket(token, roomId, onSnapshot, enabled = true) {
  const onSnapshotRef = useRef(onSnapshot);
  onSnapshotRef.current = onSnapshot; // always latest without re-running effect

  useEffect(() => {
    if (!enabled || !token || !roomId) return; // nothing to do

    let cancelled = false;
    let retryTimer = null;
    let ws = null;

    function connect() {
      if (cancelled) return;

      ws = new WebSocket(`${WS_BASE}/ws/${roomId}?token=${token}`);

      ws.onmessage = (evt) => {
        if (cancelled) return; // drop buffered frames after cleanup
        try {
          const data = JSON.parse(evt.data);
          onSnapshotRef.current(data);
        } catch { /* ignore bad frames */ }
      };

      ws.onclose = () => {
        if (!cancelled) {
          retryTimer = setTimeout(connect, 2000); // auto-reconnect
        }
        // if cancelled (cleanup ran) → do nothing, stale connection is dead
      };

      ws.onerror = () => {
        ws.close(); // triggers onclose → retry (if not cancelled)
      };
    }

    connect();

    // Cleanup: mark cancelled first so onclose never fires a stale reconnect
    return () => {
      cancelled = true;
      clearTimeout(retryTimer);
      ws?.close();
    };
  }, [enabled, token, roomId]); // reconnect only when these change
}

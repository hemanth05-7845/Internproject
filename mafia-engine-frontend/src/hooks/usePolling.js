import { useEffect } from "react";

export function usePolling(callback, intervalMs, enabled = true) {
  useEffect(() => {
    if (!enabled) {
      return;
    }

    const timer = setInterval(callback, intervalMs);
    callback();

    return () => clearInterval(timer);
  }, [callback, intervalMs, enabled]);
}

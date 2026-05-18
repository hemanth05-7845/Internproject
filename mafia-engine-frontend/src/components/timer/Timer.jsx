import { useState, useEffect } from "react";
import "./Timer.css";

export default function Timer({ phase }) {
  const [seconds, setSeconds] = useState(120); // Default 2 min

  // Map phase to duration
  const phaseDurations = {
    NIGHT: 120,
    DAY_DISCUSSION: 180,
    VOTING: 60,
    ROLE_ASSIGNMENT: 30,
  };

  useEffect(() => {
    setSeconds(phaseDurations[phase] || 120);
  }, [phase]);

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = setInterval(() => {
      setSeconds((s) => Math.max(0, s - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [seconds]);

  const minutes = Math.floor(seconds / 60);
  const secs = seconds % 60;

  return (
    <div className="timer">
      <span className="timer-display">
        ⏱️ {minutes}:{secs < 10 ? "0" : ""}{secs}
      </span>
    </div>
  );
}

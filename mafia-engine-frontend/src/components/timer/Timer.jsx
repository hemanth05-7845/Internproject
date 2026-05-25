import "./Timer.css";

export default function Timer({ seconds = 30 }) {
  const safeSeconds = Number.isFinite(seconds) && seconds >= 0 ? seconds : 30;
  const minutes = Math.floor(safeSeconds / 60);
  const secs = safeSeconds % 60;

  const urgent = safeSeconds > 0 && safeSeconds <= 10;
  const expired = safeSeconds === 0;

  return (
    <div className={`timer ${urgent ? "timer-urgent" : ""} ${expired ? "timer-expired" : ""}`}>
      <span className="timer-display">
        ⏱️ {minutes}:{secs < 10 ? "0" : ""}{secs}
      </span>
    </div>
  );
}

import "./Timer.css";

export default function Timer({ seconds = 30 }) {
  const safeSeconds = Number.isFinite(seconds) && seconds >= 0 ? seconds : 30;
  const minutes = Math.floor(safeSeconds / 60);
  const secs = safeSeconds % 60;

  return (
    <div className="timer">
      <span className="timer-display">
        ⏱️ {minutes}:{secs < 10 ? "0" : ""}{secs}
      </span>
    </div>
  );
}

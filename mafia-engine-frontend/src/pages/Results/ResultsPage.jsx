import "./ResultsPage.css";

export default function ResultsPage({ snapshot, onRestart }) {
  const winner   = snapshot?.winner || "NONE";
  const players  = snapshot?.players || [];
  const isMafia  = winner === "MAFIA";

  const winnerLabel = isMafia ? "Mafia Wins" : "Villagers Win";
  const winnerDesc  = isMafia
    ? "The mafia infiltrated the village."
    : "Every last mafia member has been eliminated.";

  return (
    <div className="results-page">
      <div className="results-card glass-lg fade-up">
        <div className={`winner-banner ${isMafia ? "mafia-win" : "village-win"}`}>
          <div className="winner-kicker">{isMafia ? "Mafia Victory" : "Village Victory"}</div>
          <h1>{winnerLabel}</h1>
          <p>{winnerDesc}</p>
        </div>

        {players.length > 0 && (
          <div className="role-reveal">
            <h3 className="reveal-heading">Role Reveal</h3>
            <div className="reveal-grid">
              {players.map((p, i) => (
                <div key={i} className={`reveal-player ${!p.alive ? "eliminated" : ""}`}>
                  <span className="reveal-avatar">{p.name?.[0]?.toUpperCase()}</span>
                  <span className="reveal-name">{p.name}</span>
                  {p.role && (
                    <span className={`role-chip role-${p.role}`}>{p.role}</span>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        <button
          id="btn-play-again"
          className="btn btn-primary btn-lg btn-full"
          onClick={() => {
            localStorage.removeItem("mafia_room_id");
            localStorage.removeItem("mafia_room_code");
            localStorage.removeItem("mafia_host_username");
            localStorage.removeItem("mafia_token");
            localStorage.removeItem("mafia_username");
            window.location.reload();
          }}
        >
          Play Again
        </button>
      </div>
    </div>
  );
}

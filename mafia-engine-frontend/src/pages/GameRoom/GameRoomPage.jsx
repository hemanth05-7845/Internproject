import { useState, useCallback, useRef, useEffect } from "react";
import { useAuth } from "../../context/AuthContext";
import {
  advancePhase, resolveVoting,
  submitVote, submitNightKill, submitPoliceGuess, submitDoctorSave, sendMessage,
} from "../../services/gameService";
import Timer from "../../components/timer/Timer";
import "./GameRoomPage.css";

function WaitingForHost({ hostUsername }) {
  return (
    <div className="waiting-host-msg">
      Waiting for <strong>{hostUsername}</strong> to advance
    </div>
  );
}

export default function GameRoomPage({ roomId, roomCode, snapshot, hostUsername }) {
  const { token, username } = useAuth();
  const [actionDone, setActionDone] = useState(false);
  const [busy, setBusy]             = useState(false);
  const [err, setErr]               = useState("");

  const phase          = snapshot?.phase || "LOADING";
  const alivePlayers   = snapshot?.alive_players   || [];
  const eliminatedPlayers = snapshot?.eliminated_players || [];
  const myRole         = snapshot?.my_role;
  const players        = snapshot?.players         || [];
  const events         = snapshot?.events          || [];
  const canVote        = snapshot?.can_vote        || false;
  const nightKill      = snapshot?.night_kill_target;
  const nightKillFailed = snapshot?.night_kill_failed;
  const policeGuess    = snapshot?.police_guess_target;
  const policeCorrect  = snapshot?.police_guess_correct;
  const dayNumber      = snapshot?.day_number      || 0;
  const nightNumber    = snapshot?.night_number    || 0;
  const remainingSeconds = snapshot?.remaining_seconds;
  const storedHost     = localStorage.getItem("mafia_host_username") || "";
  const effectiveHost  = hostUsername || snapshot?.host_username || storedHost;
  const isHost         = username && effectiveHost
    ? username.toLowerCase() === effectiveHost.toLowerCase()
    : false;
  const canChat        = alivePlayers.includes(username);
  const mafiaMembers   = snapshot?.mafia_members   || [];

  const others = alivePlayers.filter(n => n !== username);
  const mafiaTargets = others.filter(n => !mafiaMembers.includes(n));

  // Reset action flag on every phase change
  useEffect(() => { setActionDone(false); }, [phase]);

  const act = async (fn) => {
    setBusy(true); setErr("");
    try { await fn(); setActionDone(true); }
    catch (e) { setErr(e.message || "Action failed"); }
    finally { setBusy(false); }
  };

  const doAdvance  = () => act(() => advancePhase(token, roomId));
  const doResolve  = () => act(() => resolveVoting(token, roomId));
  const doVote     = (t) => act(() => submitVote(token, roomId, t));
  const doNightKill= (t) => act(() => submitNightKill(token, roomId, t));
  const doPolice   = (t) => act(() => submitPoliceGuess(token, roomId, t));
  const doDoctor   = (t) => act(() => submitDoctorSave(token, roomId, t));

  const handleLeaveRoom = () => {
    localStorage.removeItem("mafia_room_id");
    localStorage.removeItem("mafia_room_code");
    localStorage.removeItem("mafia_host_username");
    localStorage.removeItem("mafia_token");
    localStorage.removeItem("mafia_username");
    window.location.reload();
  };

  // Chat
  const [chatInput, setChatInput] = useState("");
  const [chatSending, setChatSending] = useState(false);
  const chatEndRef = useRef(null);
  const chatMessages = snapshot?.chat_messages || [];

  const doSendMsg = useCallback(async () => {
    if (!canChat) return;
    const text = chatInput.trim();
    if (!text || chatSending) return;
    setChatSending(true);
    try {
      await sendMessage(token, roomId, text);
      setChatInput("");
    } catch { /* silently ignore */ }
    finally { setChatSending(false); }
  }, [chatInput, chatSending, token, roomId]);

  const roleColor = { MAFIA: "var(--red)", POLICE: "var(--gold)", VILLAGER: "var(--blue)" };

  return (
    <div className="game-room">
      {/* Top bar */}
      <header className="game-header glass">
        <div className="header-left">
          <span className="room-code-sm">{roomCode}</span>
          <span className={`phase-badge phase-${phase}`}>{phase.replace("_", " ")}</span>
          <Timer seconds={remainingSeconds ?? 30} />
          {dayNumber > 0 && <span className="day-tag">Day {dayNumber}</span>}
          {nightNumber > 0 && <span className="night-tag">Night {nightNumber}</span>}
        </div>
        <div className="header-right">
          {isHost && <span className="host-badge">Host</span>}
          {myRole && (
            <div className="my-role-chip" style={{ borderColor: roleColor[myRole], color: roleColor[myRole] }}>
              {myRole}
            </div>
          )}
          <button id="btn-leave-room" className="btn btn-secondary"
            onClick={handleLeaveRoom}>
            Leave Room
          </button>
        </div>
      </header>

      <div className="game-body">
        {/* Left: players */}
        <aside className="players-panel glass">
          <h3 className="panel-title">Players <span className="panel-count">{alivePlayers.length} alive</span></h3>
          <div className="player-list">
            {players.map((p, i) => (
              <div key={i} className={`player-row ${!p.alive ? "dead" : ""} ${p.name === username ? "me" : ""}`}>
                <span className="player-avatar">{p.name?.[0]?.toUpperCase()}</span>
                <span className="player-name-col">{p.name}</span>
                {effectiveHost && p.name === effectiveHost && <span className="host-dot" title="Host" />}
                {/* Only reveal roles after game ends */}
                {phase === "GAME_OVER" && p.role && <span className={`role-chip role-${p.role}`}>{p.role}</span>}
              </div>
            ))}
          </div>
        </aside>

        {/* Centre: phase action */}
        <main className="action-panel">
          {phase === "LOADING" && (
            <div className="phase-card glass fade-up">
              <h2>Loading…</h2>
            </div>
          )}

          {phase === "NIGHT" && (
            <div className="phase-card glass fade-up night-card">
              <div className="phase-kicker">Night {nightNumber}</div>
              <h2>Night Phase</h2>
              {myRole === "MAFIA" && !actionDone && (
                <>
                  <p className="phase-desc">Select a player to eliminate.</p>
                  <div className="target-list">
                    {mafiaTargets.map(n => (
                      <button key={n} id={`kill-${n}`} className="target-btn kill-btn"
                        onClick={() => doNightKill(n)} disabled={busy}>
                        {n}
                      </button>
                    ))}
                  </div>
                </>
              )}
              {myRole === "MAFIA" && actionDone && (
                <p className="done-msg">Target selected — waiting for host</p>
              )}
              {myRole !== "MAFIA" && (
                <p className="phase-desc muted-msg">The village is sleeping.<br />The mafia is choosing a target.</p>
              )}
              {isHost && (
                <button id="btn-advance-night" className="btn btn-secondary host-btn"
                  onClick={doAdvance} disabled={busy}>
                  {busy ? "…" : "Advance to Police Phase"}
                </button>
              )}
              {!isHost && <WaitingForHost hostUsername={effectiveHost || "host"} />}
            </div>
          )}

          {phase === "POLICE_GUESS" && (
            <div className="phase-card glass fade-up police-card">
              <div className="phase-kicker">Police</div>
              <h2>Investigation</h2>
              {myRole === "POLICE" && !actionDone && (
                <>
                  <p className="phase-desc">Select who you believe is Mafia.</p>
                  <div className="target-list">
                    {players
                      .filter(p => p.alive && p.name !== username)
                      .map(p => (
                        <button key={p.name} id={`guess-${p.name}`} className="target-btn police-btn"
                          onClick={() => doPolice(p.name)} disabled={busy}>
                          {p.name}
                        </button>
                      ))}
                  </div>
                </>
              )}
              {myRole === "POLICE" && actionDone && (
                <p className="done-msg">Guess submitted — waiting for host</p>
              )}
              {myRole !== "POLICE" && (
                <p className="phase-desc muted-msg">The police is investigating.<br />Results announced at sunrise.</p>
              )}
              {isHost && (
                <button id="btn-advance-police" className="btn btn-secondary host-btn"
                  onClick={doAdvance} disabled={busy}>
                  Proceed to Doctor Phase
                </button>
              )}
              {!isHost && <WaitingForHost hostUsername={effectiveHost || "host"} />}
            </div>
          )}

          {phase === "DOCTOR_SAVE" && (
            <div className="phase-card glass fade-up doctor-card">
              <div className="phase-kicker">Doctor</div>
              <h2>Healing</h2>
              {myRole === "DOCTOR" && !actionDone && (
                <>
                  <p className="phase-desc">Select a player to save from tonight's kill.</p>
                  <div className="target-list">
                    {alivePlayers.map(n => (
                      <button key={n} id={`save-${n}`} className="target-btn doctor-btn"
                        onClick={() => doDoctor(n)} disabled={busy}>
                        {n}
                      </button>
                    ))}
                  </div>
                </>
              )}
              {myRole === "DOCTOR" && actionDone && (
                <p className="done-msg">Player selected — waiting for host</p>
              )}
              {myRole !== "DOCTOR" && (
                <p className="phase-desc muted-msg">The doctor is making healing decisions.</p>
              )}
              {isHost && (
                <button id="btn-advance-doctor" className="btn btn-secondary host-btn"
                  onClick={doAdvance} disabled={busy}>
                  Proceed to Sunrise
                </button>
              )}
              {!isHost && <WaitingForHost hostUsername={effectiveHost || "host"} />}
            </div>
          )}

          {phase === "SUNRISE" && (
            <div className="phase-card glass fade-up sunrise-card">
              <div className="phase-kicker">Sunrise</div>
              <h2>Night Results</h2>
              <div className="reveal-cards">
                <div className="reveal-card kill-reveal">
                  <div className="reveal-label">Night Kill</div>
                  <div className="reveal-value">
                    {nightKill ? (
                      nightKillFailed === true ? (
                        <>An attempted kill failed.</>
                      ) : (
                        <><strong>{nightKill}</strong> was eliminated.</>
                      )
                    ) : (
                      "Nobody was killed."
                    )}
                  </div>
                </div>
                {policeGuess && (
                  <div className={`reveal-card ${policeCorrect ? "correct-reveal" : "wrong-reveal"}`}>
                    <div className="reveal-label">Police Guess</div>
                    <div className="reveal-value">
                      <strong>{policeGuess}</strong>
                      {policeCorrect ? " — correct, Mafia identified." : " — incorrect."}
                    </div>
                  </div>
                )}
              </div>
              {isHost && (
                <button id="btn-advance-sunrise" className="btn btn-gold host-btn"
                  onClick={doAdvance} disabled={busy}>
                  Start Day Discussion
                </button>
              )}
              {!isHost && <WaitingForHost hostUsername={effectiveHost || "host"} />}
            </div>
          )}

          {phase === "DAY_DISCUSSION" && (
            <div className="phase-card glass fade-up day-card">
              <div className="phase-kicker">Day {dayNumber}</div>
              <h2>Discussion</h2>
              <p className="phase-desc">Discuss and identify the Mafia.</p>
              <div className="events-scroll inner-scroll">
                {events.slice(0, 8).map((e, i) => (
                  <div key={i} className="event-row">
                    <span className="event-dot" />
                    <span>{e.description || e.event}</span>
                  </div>
                ))}
              </div>
              {isHost && (
                <button id="btn-start-voting" className="btn btn-primary host-btn"
                  onClick={doAdvance} disabled={busy}>
                  Start Voting
                </button>
              )}
              {!isHost && <WaitingForHost hostUsername={effectiveHost || "host"} />}
            </div>
          )}

          {phase === "VOTING" && canVote && (
            <div className="phase-card glass fade-up voting-card">
              <div className="phase-kicker">Voting</div>
              <h2>Vote to Eliminate</h2>
              {!actionDone && (
                <>
                  <p className="phase-desc">Choose who to eliminate.</p>
                  <div className="target-list">
                    {others.map(n => (
                      <button key={n} id={`vote-${n}`} className="target-btn vote-btn"
                        onClick={() => doVote(n)} disabled={busy}>
                        {n}
                      </button>
                    ))}
                  </div>
                </>
              )}
              {actionDone && <p className="done-msg">Vote cast — waiting for host to close voting</p>}
              {isHost && (
                <button id="btn-close-voting" className="btn btn-primary host-btn"
                  onClick={doResolve} disabled={busy}>
                  Close Voting
                </button>
              )}
              {!isHost && !actionDone && <WaitingForHost hostUsername={effectiveHost || "host"} />}
            </div>
          )}

          {phase === "VOTING" && !canVote && (
            <div className="phase-card glass fade-up voting-card">
              <div className="phase-kicker">Voting</div>
              <h2>Vote to Eliminate</h2>
              <p className="phase-desc muted-msg">You are not eligible to vote in this round.</p>
              {isHost && (
                <button id="btn-close-voting" className="btn btn-primary host-btn"
                  onClick={doResolve} disabled={busy}>
                  Close Voting
                </button>
              )}
              {!isHost && <WaitingForHost hostUsername={effectiveHost || "host"} />}
            </div>
          )}

          {(phase === "ELIMINATION" || phase === "WIN_CHECK") && (
            <div className="phase-card glass fade-up">
              <h2>Processing…</h2>
              <p className="phase-desc muted-msg">Checking results</p>
            </div>
          )}

          {phase === "GAME_OVER" && (
            <div className="phase-card glass fade-up">
              <div className="phase-kicker">Game Over</div>
              <h2 className={snapshot?.winner === "MAFIA" ? "text-red" : "text-gold"}>
                {snapshot?.winner === "MAFIA" ? "Mafia Wins" : "Villagers Win"}
              </h2>
              <p className="phase-desc">
                {snapshot?.winner === "MAFIA"
                  ? "The mafia has taken over the village."
                  : "The village has defeated the mafia."}
              </p>
            </div>
          )}

          {err && <div className="error-msg" style={{ marginTop: 12 }}>{err}</div>}
        </main>

        {/* Right: chat + events */}
        <aside className="events-panel glass">
          <h3 className="panel-title">Chat</h3>
          <div className="chat-messages">
            {chatMessages.length === 0 && <p className="no-events">No messages yet</p>}
            {[...chatMessages].reverse().map((m, i) => (
              <div key={i} className={`chat-bubble ${m.sender === username ? "mine" : "theirs"}`}>
                <span className="chat-sender">{m.sender === username ? "You" : m.sender}</span>
                <span className="chat-text">{m.message || m.content}</span>
              </div>
            ))}
            <div ref={chatEndRef} />
          </div>
          <div className="chat-input-row">
            <input
              id="chat-input"
              className="input chat-input"
              placeholder="Message…"
              value={chatInput}
              maxLength={280}
              onChange={e => setChatInput(e.target.value)}
              onKeyDown={e => e.key === "Enter" && doSendMsg()}
              disabled={chatSending || !canChat}
            />
            <button id="btn-send-chat" className="btn btn-secondary send-btn"
              onClick={doSendMsg} disabled={chatSending || !canChat || !chatInput.trim()}>
              Send
            </button>
          </div>

          <h3 className="panel-title" style={{ marginTop: 16 }}>Events</h3>
          <div className="events-scroll">
            {events.length === 0 && <p className="no-events">Game in progress</p>}
            {events.map((e, i) => (
              <div key={i} className="event-row">
                <span className="event-dot" />
                <span>{e.description || e.event || e.type}</span>
              </div>
            ))}
          </div>

          {eliminatedPlayers.length > 0 && (
            <div className="eliminated-section">
              <h4 className="panel-title">Eliminated</h4>
              {eliminatedPlayers.map((n, i) => (
                <div key={i} className="eliminated-name">{n}</div>
              ))}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

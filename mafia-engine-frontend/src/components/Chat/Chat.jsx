import { useState, useRef, useEffect } from "react";
import "./Chat.css";

export default function Chat({ messages, onSendMessage }) {
  const [input, setInput] = useState("");
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSend = () => {
    if (input.trim()) {
      onSendMessage(input);
      setInput("");
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="chat-panel">
      <div className="panel-section">
        <h3>💬 Chat</h3>
        <div className="messages-container">
          {messages.length === 0 ? (
            <p className="no-messages">No messages yet</p>
          ) : (
            messages.map((msg, i) => (
              <div key={i} className="message">
                <strong className="message-sender">{msg.sender}:</strong>
                <span className="message-text">{msg.text}</span>
              </div>
            ))
          )}
          <div ref={messagesEndRef} />
        </div>
        <div className="chat-input-area">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="Type a message..."
            className="chat-input"
            rows={2}
          />
          <button className="chat-send-btn" onClick={handleSend}>
            Send
          </button>
        </div>
      </div>
    </div>
  );
}

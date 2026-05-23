import { createContext, useContext, useEffect, useMemo, useState } from "react";

const STORAGE_TOKEN = "mafia_token";
const STORAGE_USER = "mafia_username";

const decodeJwtSubject = (token) => {
  try {
    const base64 = token.split(".")[1] || "";
    const normalized = base64.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized + "===".slice((normalized.length + 3) % 4);
    const payload = JSON.parse(atob(padded));
    return payload.sub || payload.username || "";
  } catch {
    return "";
  }
};

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(STORAGE_TOKEN) || "");
  const [username, setUsername] = useState(() => {
    const stored = localStorage.getItem(STORAGE_USER) || "";
    if (stored) return stored;
    const storedToken = localStorage.getItem(STORAGE_TOKEN) || "";
    return storedToken ? decodeJwtSubject(storedToken) : "";
  });

  useEffect(() => {
    if (token) localStorage.setItem(STORAGE_TOKEN, token);
    else localStorage.removeItem(STORAGE_TOKEN);
  }, [token]);

  useEffect(() => {
    if (username) localStorage.setItem(STORAGE_USER, username);
    else localStorage.removeItem(STORAGE_USER);
  }, [username]);

  const value = useMemo(
    () => ({ token, setToken, username, setUsername }),
    [token, username]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}

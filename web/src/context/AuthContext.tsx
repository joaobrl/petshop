import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import * as authService from "../services/authService";
import { decodeToken, isExpired, type DecodedToken } from "../services/authService";
import { authEvents, getToken, SESSION_EXPIRED_EVENT, setToken } from "../services/http";
import type { LoginResponse } from "../types/auth";
import type { Role } from "../types/staff";

interface AuthContextValue {
  user: DecodedToken | null;
  isAuthenticated: boolean;
  role: Role | null;
  mustChangePassword: boolean;
  sessionExpired: boolean;
  login: (email: string, password: string) => Promise<LoginResponse>;
  logout: () => void;
  clearMustChangePassword: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function loadInitialUser(): DecodedToken | null {
  const token = getToken();
  if (!token) return null;
  try {
    const decoded = decodeToken(token);
    if (isExpired(decoded)) {
      setToken(null);
      return null;
    }
    return decoded;
  } catch {
    setToken(null);
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<DecodedToken | null>(loadInitialUser);
  const [mustChangePassword, setMustChangePassword] = useState(false);
  const [sessionExpired, setSessionExpired] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const currentPath = location.pathname + location.search;
  const isInternal = location.pathname.startsWith("/interno");
  const loginPath = isInternal ? "/interno/entrar" : "/entrar";
  const onLoginPage = location.pathname === loginPath;

  const handleSessionExpired = useCallback(() => {
    setToken(null);
    setSessionExpired(true);
    setUser(null);
    setMustChangePassword(false);
    if (onLoginPage) return;
    navigate(`${loginPath}?expired=1&next=${encodeURIComponent(currentPath)}`, { replace: true });
  }, [navigate, currentPath, onLoginPage, loginPath]);

  useEffect(() => {
    authEvents.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => authEvents.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
  }, [handleSessionExpired]);

  // Log out at the token's exp for users who stay idle on a screen.
  const exp = user?.exp;
  useEffect(() => {
    if (exp === undefined) return;
    const delay = Math.min(Math.max(exp * 1000 - Date.now(), 0), 2147483647);
    const timer = setTimeout(() => {
      // setTimeout caps at ~24.8 days; re-check in case we fired early.
      if (exp * 1000 > Date.now()) return;
      handleSessionExpired();
    }, delay);
    return () => clearTimeout(timer);
  }, [exp, handleSessionExpired]);

  async function login(email: string, password: string) {
    const response = await authService.login(email, password);
    setToken(response.accessToken);
    setSessionExpired(false);
    setUser(decodeToken(response.accessToken));
    setMustChangePassword(response.mustChangePassword);
    return response;
  }

  function logout() {
    setToken(null);
    setSessionExpired(false);
    setUser(null);
    setMustChangePassword(false);
  }

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    role: user?.role ?? null,
    mustChangePassword,
    sessionExpired,
    login,
    logout,
    clearMustChangePassword: () => setMustChangePassword(false),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}

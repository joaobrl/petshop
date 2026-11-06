import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import type { User } from "oidc-client-ts";
import { decodeToken, type DecodedToken } from "../services/authService";
import { authEvents, SESSION_EXPIRED_EVENT } from "../services/http";
import * as oidc from "../services/oidc";
import type { Role } from "../types/staff";

interface AuthContextValue {
  user: DecodedToken | null;
  isAuthenticated: boolean;
  role: Role | null;
  // true until the silent SSO check on page load finishes (guards wait for it).
  initializing: boolean;
  sessionExpired: boolean;
  // Redirects to the Keycloak login page (Authorization Code + PKCE).
  login: (options?: { next?: string; loginHint?: string }) => Promise<void>;
  // end_session of Keycloak, then back to "/".
  logout: () => Promise<void>;
  // New token from Keycloak (e.g. after confirming the e-mail, to get email_verified=true).
  refreshSession: () => Promise<boolean>;
  completeLogin: () => Promise<string | null>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function toDecoded(user: User | null): DecodedToken | null {
  if (!user || !user.access_token) return null;
  try {
    return decodeToken(user.access_token);
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<DecodedToken | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const currentPath = location.pathname + location.search;
  const isInternal = location.pathname.startsWith("/interno");
  const loginPath = isInternal ? "/interno/entrar" : "/entrar";
  const onLoginPage = location.pathname === loginPath;
  const onCallback = location.pathname === oidc.CALLBACK_PATH;

  // Tokens live only in memory: on load, recover the session through the Keycloak SSO
  // cookie (silent sign-in). No session = anonymous. The callback route does its own exchange.
  useEffect(() => {
    if (onCallback) {
      setInitializing(false);
      return;
    }
    let active = true;
    oidc.restoreSession().then((restored) => {
      if (!active) return;
      setUser(toDecoded(restored));
      setInitializing(false);
    });
    return () => {
      active = false;
    };
    // Only once per page load.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Silent renewals (refresh token) and logout keep the decoded user in sync.
  useEffect(() => {
    const offLoaded = oidc.userManager.events.addUserLoaded((loaded) => {
      setUser(toDecoded(loaded));
      setSessionExpired(false);
    });
    const offUnloaded = oidc.userManager.events.addUserUnloaded(() => setUser(null));
    return () => {
      offLoaded();
      offUnloaded();
    };
  }, []);

  const handleSessionExpired = useCallback(() => {
    setSessionExpired(true);
    setUser(null);
    void oidc.userManager.removeUser();
    if (onLoginPage) return;
    navigate(`${loginPath}?expired=1&next=${encodeURIComponent(currentPath)}`, { replace: true });
  }, [navigate, currentPath, onLoginPage, loginPath]);

  useEffect(() => {
    authEvents.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => authEvents.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
  }, [handleSessionExpired]);

  // Access token reached exp without a successful automatic renewal: try once more,
  // then fall back to the session-expired flow (SDD-015).
  useEffect(() => {
    return oidc.userManager.events.addAccessTokenExpired(() => {
      void oidc.renewToken().then((token) => {
        if (!token) handleSessionExpired();
      });
    });
  }, [handleSessionExpired]);

  const login = useCallback((options?: { next?: string; loginHint?: string }) => oidc.login(options), []);

  const logout = useCallback(async () => {
    setSessionExpired(false);
    setUser(null);
    await oidc.logout();
  }, []);

  const refreshSession = useCallback(async () => {
    const token = await oidc.renewToken();
    return token !== null;
  }, []);

  const completeLogin = useCallback(async () => {
    const signedIn = await oidc.completeLogin();
    setUser(toDecoded(signedIn));
    setSessionExpired(false);
    const state = signedIn.state as oidc.LoginState | undefined;
    return state?.next ?? null;
  }, []);

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    role: user?.role ?? null,
    initializing,
    sessionExpired,
    login,
    logout,
    refreshSession,
    completeLogin,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}

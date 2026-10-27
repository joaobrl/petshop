import { createContext, useContext, useState, type ReactNode } from "react";
import * as authService from "../services/authService";
import { decodeToken, type DecodedToken } from "../services/authService";
import { getToken, setToken } from "../services/http";
import type { LoginResponse } from "../types/auth";
import type { Role } from "../types/staff";

interface AuthContextValue {
  user: DecodedToken | null;
  isAuthenticated: boolean;
  role: Role | null;
  mustChangePassword: boolean;
  login: (email: string, password: string) => Promise<LoginResponse>;
  logout: () => void;
  clearMustChangePassword: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function loadInitialUser(): DecodedToken | null {
  const token = getToken();
  if (!token) return null;
  try {
    return decodeToken(token);
  } catch {
    setToken(null);
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<DecodedToken | null>(loadInitialUser);
  const [mustChangePassword, setMustChangePassword] = useState(false);

  async function login(email: string, password: string) {
    const response = await authService.login(email, password);
    setToken(response.accessToken);
    setUser(decodeToken(response.accessToken));
    setMustChangePassword(response.mustChangePassword);
    return response;
  }

  function logout() {
    setToken(null);
    setUser(null);
    setMustChangePassword(false);
  }

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    role: user?.role ?? null,
    mustChangePassword,
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

import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function RequireStaff({ children }: { children: ReactNode }) {
  const { role, sessionExpired } = useAuth();
  const location = useLocation();
  if (!role || role === "CUSTOMER") {
    // Same target AuthContext navigates to, so the two redirects never disagree.
    const to = sessionExpired
      ? `/interno/entrar?expired=1&next=${encodeURIComponent(location.pathname + location.search)}`
      : "/interno/entrar";
    return <Navigate to={to} replace />;
  }
  return <>{children}</>;
}

import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function RequireStaff({ children }: { children: ReactNode }) {
  const { role, sessionExpired, initializing } = useAuth();
  const location = useLocation();
  // Waits for the silent SSO check on page load before deciding.
  if (initializing) return null;
  if (!role || role === "CUSTOMER") {
    // Same target AuthContext navigates to, so the two redirects never disagree.
    const next = encodeURIComponent(location.pathname + location.search);
    const to = sessionExpired ? `/interno/entrar?expired=1&next=${next}` : `/interno/entrar?next=${next}`;
    return <Navigate to={to} replace />;
  }
  return <>{children}</>;
}

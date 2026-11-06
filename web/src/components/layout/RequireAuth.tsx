import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, sessionExpired, initializing } = useAuth();
  const location = useLocation();

  // Waits for the silent SSO check on page load before deciding.
  if (initializing) return null;
  if (!isAuthenticated) {
    // Same target AuthContext navigates to, so the two redirects never disagree.
    const next = encodeURIComponent(location.pathname + location.search);
    const to = sessionExpired ? `/entrar?expired=1&next=${next}` : `/entrar?next=${next}`;
    return <Navigate to={to} state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

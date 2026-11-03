import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, sessionExpired } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    // Same target AuthContext navigates to, so the two redirects never disagree.
    const to = sessionExpired
      ? `/entrar?expired=1&next=${encodeURIComponent(location.pathname + location.search)}`
      : "/entrar";
    return <Navigate to={to} state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

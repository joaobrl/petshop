import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function RequireStaff({ children }: { children: ReactNode }) {
  const { role } = useAuth();
  if (!role || role === "CUSTOMER") {
    return <Navigate to="/interno/entrar" replace />;
  }
  return <>{children}</>;
}

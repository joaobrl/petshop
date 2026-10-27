import type { ReactNode } from "react";

type Tone = "warning" | "info" | "success" | "danger";

export function Badge({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <span className={`badge badge-${tone}`}>
      <span className="badge-dot" />
      {children}
    </span>
  );
}

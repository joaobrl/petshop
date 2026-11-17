import type { CSSProperties, ReactNode } from "react";
import { Polaroid } from "../ui/Polaroid";
import { PawTrail } from "../ui/PawTrail";

interface AuthShellProps {
  // Left panel (hidden on small screens): a greeting and a pet snapshot.
  greeting: string;
  photo: string;
  caption: string;
  tile?: string;
  children: ReactNode;
}

export function AuthShell({ greeting, photo, caption, tile = "var(--primary)", children }: AuthShellProps) {
  return (
    <div className="auth">
      <div className="auth-aside" style={{ "--tile": tile } as CSSProperties}>
        <p className="title">{greeting}</p>
        <Polaroid src={photo} caption={caption} tilt={4} />
        <PawTrail />
      </div>
      <div className="auth-main">{children}</div>
    </div>
  );
}

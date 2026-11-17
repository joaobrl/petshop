import type { CSSProperties } from "react";

const TILES = ["var(--bath-tint)", "var(--primary-tint)", "var(--snout-tint)", "var(--grass-tint)"];

export function InitialsAvatar({ name, size = 40 }: { name: string; size?: number }) {
  const initials = name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
  // Same person, same colour — picked from the name, not from list position.
  const tile = TILES[[...name].reduce((sum, ch) => sum + ch.charCodeAt(0), 0) % TILES.length];

  return (
    <div
      className="initials-avatar"
      aria-hidden="true"
      style={{ width: size, height: size, fontSize: size * 0.4, "--tile": tile } as CSSProperties}
    >
      {initials}
    </div>
  );
}

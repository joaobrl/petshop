import type { ReactNode } from "react";

export function PawBackground({
  tint = "var(--secondary-tint)",
  children,
}: {
  tint?: string;
  children: ReactNode;
}) {
  return (
    <div
      style={{
        position: "relative",
        borderRadius: "var(--radius-lg)",
        overflow: "hidden",
        background: tint,
      }}
    >
      <svg
        width="100%"
        height="100%"
        style={{ position: "absolute", inset: 0 }}
        preserveAspectRatio="none"
      >
        <defs>
          <pattern id="pawTile" width="96" height="96" patternUnits="userSpaceOnUse">
            <g fill="var(--ink)" opacity="0.08">
              <ellipse cx="24" cy="46" rx="8" ry="9" />
              <circle cx="13" cy="33" r="4.5" />
              <circle cx="20" cy="27" r="5" />
              <circle cx="28" cy="27" r="5" />
              <circle cx="35" cy="33" r="4.5" />
            </g>
            <g
              fill="var(--primary)"
              opacity="0.12"
              transform="translate(58,58) rotate(15)"
            >
              <ellipse cx="12" cy="24" rx="6.5" ry="6.5" />
              <circle cx="4" cy="14" r="3.6" />
              <circle cx="9" cy="9" r="3.8" />
              <circle cx="15" cy="9" r="3.8" />
              <circle cx="20" cy="14" r="3.6" />
            </g>
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#pawTile)" />
      </svg>
      <div style={{ position: "relative" }}>{children}</div>
    </div>
  );
}

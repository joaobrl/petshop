interface IconProps {
  size?: number;
  color?: string;
}

// Minimal stroke-icon set (20–24px, consistent weight) so the interface
// never falls back to emoji — see the design system's Iconography note.
export function CartIcon({ size = 22, color = "var(--ink)" }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <circle cx="9" cy="20" r="1.4" />
      <circle cx="18" cy="20" r="1.4" />
      <path d="M2.5 3h2l2.4 12.2a2 2 0 0 0 2 1.6h8.2a2 2 0 0 0 2-1.6L21 7H6" />
    </svg>
  );
}

export function PawIcon({ size = 22, color = "var(--ink)" }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color}>
      <ellipse cx="12" cy="16.5" rx="5.2" ry="5.6" />
      <circle cx="5.2" cy="10" r="2.4" />
      <circle cx="9.4" cy="5.6" r="2.6" />
      <circle cx="14.6" cy="5.6" r="2.6" />
      <circle cx="18.8" cy="10" r="2.4" />
    </svg>
  );
}

export function CalendarIcon({ size = 22, color = "var(--ink)" }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="5" width="18" height="16" rx="3" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </svg>
  );
}

export function CheckCircleIcon({ size = 48, color = "var(--success)" }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9.5" />
      <path d="M8 12.5l2.5 2.5L16 9.5" />
    </svg>
  );
}

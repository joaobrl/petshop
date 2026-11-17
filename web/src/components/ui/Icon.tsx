import type { ReactNode } from "react";
import type { ServiceType } from "../../types/booking";

interface IconProps {
  size?: number;
  color?: string;
}

// Hand-rolled stroke icons (24px grid, one weight) so the interface never
// falls back to emoji or to a generic icon pack.
function Stroke({ size = 22, color = "currentColor", children }: IconProps & { children: ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={1.9}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

export function CartIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M5 8h14l-1.2 10.2a2 2 0 0 1-2 1.8H8.2a2 2 0 0 1-2-1.8z" />
      <path d="M9 8V6.5a3 3 0 0 1 6 0V8" />
    </Stroke>
  );
}

export function PawIcon({ size = 22, color = "currentColor" }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color} aria-hidden="true" focusable="false">
      <path d="M12 11.2c-3 0-5.6 2.9-5.6 5.7 0 2 1.5 3.1 3.2 3.1 1 0 1.6-.5 2.4-.5s1.4.5 2.4.5c1.7 0 3.2-1.1 3.2-3.1 0-2.8-2.6-5.7-5.6-5.7z" />
      <ellipse cx="5.2" cy="10.4" rx="2.1" ry="2.6" transform="rotate(-18 5.2 10.4)" />
      <ellipse cx="9.3" cy="5.8" rx="2.2" ry="2.8" transform="rotate(-6 9.3 5.8)" />
      <ellipse cx="14.7" cy="5.8" rx="2.2" ry="2.8" transform="rotate(6 14.7 5.8)" />
      <ellipse cx="18.8" cy="10.4" rx="2.1" ry="2.6" transform="rotate(18 18.8 10.4)" />
    </svg>
  );
}

export function CalendarIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <rect x="3.5" y="5" width="17" height="15.5" rx="3" />
      <path d="M3.5 10h17M8 3v4M16 3v4" />
    </Stroke>
  );
}

export function CheckCircleIcon({ size = 48, color = "var(--success)" }: IconProps) {
  return (
    <Stroke size={size} color={color}>
      <circle cx="12" cy="12" r="9.5" />
      <path d="M8 12.5l2.5 2.5L16 9.5" />
    </Stroke>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </Stroke>
  );
}

export function PlusIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M12 5v14M5 12h14" />
    </Stroke>
  );
}

export function MinusIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M5 12h14" />
    </Stroke>
  );
}

export function MenuIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M4 7h16M4 12h16M4 17h10" />
    </Stroke>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M6 6l12 12M18 6L6 18" />
    </Stroke>
  );
}

export function TrashIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l.8 11.2a2 2 0 0 0 2 1.8h5.4a2 2 0 0 0 2-1.8L17.5 7M10 11v5M14 11v5" />
    </Stroke>
  );
}

export function SearchIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="M15.5 15.5L20 20" />
    </Stroke>
  );
}

export function ArrowRightIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M4 12h15M13.5 6.5L19 12l-5.5 5.5" />
    </Stroke>
  );
}

export function PinIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M12 21s-6.5-5.8-6.5-10.7a6.5 6.5 0 0 1 13 0C18.5 15.2 12 21 12 21z" />
      <circle cx="12" cy="10.2" r="2.3" />
    </Stroke>
  );
}

export function ClockIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </Stroke>
  );
}

export function LogoutIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M10 4.5H7a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h3M15 8l4 4-4 4M19 12H10" />
    </Stroke>
  );
}

export function UsersIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <circle cx="9" cy="8.5" r="3.2" />
      <path d="M3 19.5c.4-3.3 2.8-5.3 6-5.3s5.6 2 6 5.3M15.5 5.6a3.2 3.2 0 0 1 0 5.8M17.5 14.6c2 .6 3.2 2.3 3.5 4.9" />
    </Stroke>
  );
}

export function HomeIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M4 11l8-6.5 8 6.5v7.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5zM9.5 20v-5.5h5V20" />
    </Stroke>
  );
}

export function InfoIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5M12 7.8v.2" />
    </Stroke>
  );
}

// --- services ---

export function BathIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M3.5 12.5h17v1.5a5 5 0 0 1-5 5h-7a5 5 0 0 1-5-5zM6.5 19l-1 1.8M17.5 19l1 1.8M6 12.5V6.3a2.3 2.3 0 0 1 4.5-.6" />
      <circle cx="14.5" cy="7.5" r="1.2" />
      <circle cx="17.8" cy="5" r=".8" />
    </Stroke>
  );
}

export function ScissorsIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <circle cx="6.5" cy="7" r="2.7" />
      <circle cx="6.5" cy="17" r="2.7" />
      <path d="M8.8 8.5L20 17M8.8 15.5L20 7" />
    </Stroke>
  );
}

export function SparkleIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M10 4l1.7 4.8L16.5 10.5l-4.8 1.7L10 17l-1.7-4.8L3.5 10.5l4.8-1.7zM18 14.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z" />
    </Stroke>
  );
}

export function VetIcon(props: IconProps) {
  return (
    <Stroke {...props}>
      <path d="M12 20s-7.5-4.4-7.5-10A4.2 4.2 0 0 1 12 7.4 4.2 4.2 0 0 1 19.5 10c0 5.6-7.5 10-7.5 10z" />
      <path d="M12 10.2v4.6M9.7 12.5h4.6" />
    </Stroke>
  );
}

export function ServiceIcon({ type, ...props }: IconProps & { type: ServiceType }) {
  if (type === "BANHO") return <BathIcon {...props} />;
  if (type === "TOSAGEM") return <ScissorsIcon {...props} />;
  if (type === "TOSA_E_BANHO") return <SparkleIcon {...props} />;
  return <VetIcon {...props} />;
}

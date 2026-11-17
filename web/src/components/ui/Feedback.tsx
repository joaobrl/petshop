import type { ReactNode } from "react";
import { InfoIcon, PawIcon } from "./Icon";

export function Loading({ children = "Só um instante…" }: { children?: ReactNode }) {
  return (
    <p className="loading" role="status">
      <span className="loading-paws" aria-hidden="true">
        <PawIcon size={18} />
        <PawIcon size={18} />
        <PawIcon size={18} />
      </span>
      {children}
    </p>
  );
}

type Tone = "info" | "danger" | "warning" | "success" | "muted";

export function Notice({ tone = "info", children }: { tone?: Tone; children: ReactNode }) {
  const className = tone === "info" ? "notice" : `notice notice-${tone}`;
  return (
    <div className={className} role={tone === "danger" ? "alert" : undefined}>
      <InfoIcon size={20} />
      <div>{children}</div>
    </div>
  );
}

interface PageHeaderProps {
  eyebrow?: string;
  title: string;
  children?: ReactNode; // actions on the right
}

export function PageHeader({ eyebrow, title, children }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div>
        {eyebrow && <span className="eyebrow">{eyebrow}</span>}
        <h1 className="title">{title}</h1>
      </div>
      {children && <div className="row row-wrap">{children}</div>}
    </header>
  );
}

// An empty food bowl — the universal "nothing here yet".
function EmptyBowl() {
  return (
    <svg className="empty-art" viewBox="0 0 160 112" aria-hidden="true" focusable="false">
      <ellipse cx="80" cy="100" rx="66" ry="8" fill="var(--ink)" opacity="0.08" />
      <path
        d="M26 52h108l-11 38a10 10 0 0 1-10 7H47a10 10 0 0 1-10-7z"
        fill="var(--primary)"
        stroke="var(--ink)"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <ellipse cx="80" cy="52" rx="54" ry="11" fill="#fff" stroke="var(--ink)" strokeWidth="3" />
      <ellipse cx="80" cy="54" rx="40" ry="6" fill="var(--surface-sunken)" />
      <g fill="var(--ink)">
        <ellipse cx="80" cy="81" rx="6.5" ry="5.5" />
        <circle cx="71.5" cy="74" r="2.6" />
        <circle cx="77" cy="68.5" r="2.8" />
        <circle cx="83" cy="68.5" r="2.8" />
        <circle cx="88.5" cy="74" r="2.6" />
      </g>
      <path d="M120 22l6-8M132 30l9-4M108 18l-1-10" stroke="var(--ink)" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
}

interface EmptyStateProps {
  title: string;
  note?: string; // handwritten aside
  children?: ReactNode;
}

export function EmptyState({ title, note, children }: EmptyStateProps) {
  return (
    <div className="empty">
      <EmptyBowl />
      <h2 className="heading">{title}</h2>
      {note && <p className="hand">{note}</p>}
      {children}
    </div>
  );
}

import type { CSSProperties, ReactNode } from "react";

// The catalog has no product photos, so each product gets a small flat
// illustration picked from its name, on a paper tile whose colour rotates
// with the product id. Unknown products fall back to a bone.

const INK = {
  stroke: "var(--ink)",
  strokeWidth: 3,
  strokeLinejoin: "round" as const,
  strokeLinecap: "round" as const,
};

function Bag() {
  return (
    <>
      <path d="M36 32h48l5 62a7 7 0 0 1-7 8H38a7 7 0 0 1-7-8z" fill="var(--primary)" {...INK} />
      <path d="M34 20h52l-2 12H36z" fill="#fff" {...INK} />
      <path d="M42 26h36" {...INK} strokeWidth={2} />
      <circle cx="60" cy="68" r="17" fill="#fff" {...INK} />
      <g fill="var(--ink)">
        <ellipse cx="60" cy="73" rx="6.5" ry="5.5" />
        <circle cx="51.5" cy="66" r="2.6" />
        <circle cx="57" cy="60.5" r="2.8" />
        <circle cx="63" cy="60.5" r="2.8" />
        <circle cx="68.5" cy="66" r="2.6" />
      </g>
    </>
  );
}

function Litter() {
  return (
    <>
      <path d="M30 60c7-13 15-5 21-11s15-9 21-1 12 1 18 12z" fill="var(--kraft)" {...INK} />
      <path d="M22 58h76l-7 36a7 7 0 0 1-7 6H36a7 7 0 0 1-7-6z" fill="var(--bath)" {...INK} />
      <path d="M22 58h76" {...INK} strokeWidth={6} />
      <g fill="var(--ink)">
        <circle cx="46" cy="50" r="1.6" />
        <circle cx="58" cy="44" r="1.6" />
        <circle cx="70" cy="49" r="1.6" />
        <circle cx="80" cy="54" r="1.6" />
      </g>
    </>
  );
}

function Ball() {
  return (
    <>
      <circle cx="60" cy="62" r="32" fill="var(--primary)" {...INK} />
      <path d="M36 41c15 9 15 33 0 42M84 41c-15 9-15 33 0 42" fill="none" stroke="#fff" strokeWidth={5} strokeLinecap="round" />
      <path d="M22 24l6 5M98 24l-6 5M60 16v7" {...INK} strokeWidth={2.5} />
    </>
  );
}

function Collar() {
  return (
    <>
      <ellipse cx="60" cy="50" rx="35" ry="21" fill="none" stroke="var(--ink)" strokeWidth={15} />
      <ellipse cx="60" cy="50" rx="35" ry="21" fill="none" stroke="var(--snout)" strokeWidth={9} />
      <rect x="51" y="62" width="18" height="14" rx="3" fill="var(--kraft)" {...INK} />
      <circle cx="60" cy="92" r="13" fill="var(--primary)" {...INK} />
      <path d="M60 76v4" {...INK} />
      <path d="M55 92l3.5 3.5 6.5-7" fill="none" {...INK} strokeWidth={2.5} />
    </>
  );
}

function Bottle() {
  return (
    <>
      <path d="M62 30h18v6" fill="none" {...INK} />
      <rect x="50" y="24" width="20" height="9" rx="2" fill="var(--ink)" {...INK} />
      <rect x="54" y="33" width="12" height="12" fill="#fff" {...INK} />
      <rect x="40" y="45" width="40" height="58" rx="10" fill="var(--bath)" {...INK} />
      <rect x="47" y="60" width="26" height="28" rx="5" fill="#fff" {...INK} strokeWidth={2.5} />
      <path d="M60 66c4 5 6 8 6 11a6 6 0 0 1-12 0c0-3 2-6 6-11z" fill="var(--bath)" {...INK} strokeWidth={2.5} />
      <circle cx="92" cy="40" r="5" fill="#fff" {...INK} strokeWidth={2.5} />
      <circle cx="100" cy="56" r="3" fill="#fff" {...INK} strokeWidth={2.5} />
    </>
  );
}

function ScratchPost() {
  return (
    <>
      <rect x="26" y="92" width="68" height="11" rx="4" fill="var(--kraft)" {...INK} />
      <rect x="50" y="38" width="20" height="54" fill="var(--snout)" {...INK} />
      <path d="M50 49h20M50 60h20M50 71h20M50 82h20" {...INK} strokeWidth={2.5} />
      <rect x="32" y="26" width="56" height="12" rx="5" fill="var(--grass)" {...INK} />
      <path d="M82 38v14" {...INK} strokeWidth={2.5} />
      <circle cx="82" cy="59" r="7" fill="var(--primary)" {...INK} />
    </>
  );
}

function Bed() {
  return (
    <>
      <path d="M16 76c0-34 88-34 88 0v6c0 20-88 20-88 0z" fill="var(--bath)" {...INK} />
      <ellipse cx="60" cy="78" rx="31" ry="12" fill="#fff" {...INK} />
      <path d="M44 76c5-5 11-5 16 0s11 5 16 0" fill="none" {...INK} strokeWidth={2.5} />
      <path d="M84 30h10l-10 11h10M100 16h7l-7 8h7" fill="none" {...INK} strokeWidth={2.5} />
    </>
  );
}

function Bone() {
  return (
    <g transform="rotate(-28 60 62)">
      <circle cx="30" cy="51" r="12" fill="#fff" {...INK} />
      <circle cx="30" cy="73" r="12" fill="#fff" {...INK} />
      <circle cx="90" cy="51" r="12" fill="#fff" {...INK} />
      <circle cx="90" cy="73" r="12" fill="#fff" {...INK} />
      <rect x="32" y="52" width="56" height="20" fill="#fff" {...INK} />
      <rect x="28" y="54" width="64" height="16" fill="#fff" />
    </g>
  );
}

const ART: { match: RegExp; draw: () => ReactNode }[] = [
  { match: /areia|higien|granulado/, draw: Litter },
  { match: /racao|alimento|petisco|sache|biscoito/, draw: Bag },
  { match: /shampoo|condicionador|sabonete|perfume|banho/, draw: Bottle },
  { match: /coleira|guia|peitoral/, draw: Collar },
  { match: /arranhador/, draw: ScratchPost },
  { match: /cama|caminha|almofada|colchonete/, draw: Bed },
  { match: /brinquedo|mordedor|bola|bolinha|corda/, draw: Ball },
];

const TILES = ["var(--primary-tint)", "var(--bath-tint)", "var(--snout-tint)", "var(--grass-tint)"];

function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();
}

interface ProductArtProps {
  name: string;
  seed: number;
  out?: boolean;
}

export function ProductArt({ name, seed, out = false }: ProductArtProps) {
  const key = normalize(name);
  const Draw = ART.find((entry) => entry.match.test(key))?.draw ?? Bone;
  const style = { "--tile": TILES[Math.abs(seed) % TILES.length] } as CSSProperties;
  return (
    <div className={out ? "product-art is-out" : "product-art"} style={style}>
      <svg viewBox="0 0 120 120" aria-hidden="true" focusable="false">
        <Draw />
      </svg>
    </div>
  );
}

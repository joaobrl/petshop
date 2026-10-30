import { Badge } from "../ui/Badge";
import type { StockRange } from "../../types/product";

// The catalog only ever exposes a band, never the exact quantity
// (product-registration's Product#stockRange).
const LABEL: Record<StockRange, string> = {
  "50+": "50+ disponíveis",
  "20+": "20+ disponíveis",
  LOW: "Poucas unidades",
};

export function StockBadge({ stockRange }: { stockRange: StockRange | null }) {
  if (!stockRange) return <Badge tone="danger">Sem estoque</Badge>;
  return <Badge tone={stockRange === "LOW" ? "warning" : "success"}>{LABEL[stockRange]}</Badge>;
}

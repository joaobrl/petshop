import { dateParts } from "../../utils/date";

// A tear-off calendar leaf: month on the coloured strip, day below.
// Decorative — the row next to it always states the date/time in text.
export function DateLeaf({ value }: { value: string }) {
  const parts = dateParts(value);
  if (!parts) return null;
  return (
    <span className="date-leaf" aria-label={`${parts.day} de ${parts.month}`}>
      <span className="date-leaf-month" aria-hidden="true">
        {parts.month}
      </span>
      <span className="date-leaf-day" aria-hidden="true">
        {parts.day}
      </span>
    </span>
  );
}

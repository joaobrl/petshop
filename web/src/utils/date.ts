const TIME_ZONE = "America/Sao_Paulo";
const NAIVE_ISO = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$/;
const INVALID = "—";

// Values with an explicit zone (Date, ISO with Z/offset) are converted to Brasilia time.
const zonedDateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  timeZone: TIME_ZONE,
  dateStyle: "short",
  timeStyle: "short",
});
const zonedDateFormatter = new Intl.DateTimeFormat("pt-BR", {
  timeZone: TIME_ZONE,
  dateStyle: "short",
});

// Wall-clock values (no zone) already are Brasilia time: they are shown as written,
// so they are built as UTC and formatted in UTC to avoid any browser-zone shift.
const wallClockDateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  timeZone: "UTC",
  dateStyle: "short",
  timeStyle: "short",
});
const wallClockDateFormatter = new Intl.DateTimeFormat("pt-BR", {
  timeZone: "UTC",
  dateStyle: "short",
});

function isValid(date: Date): boolean {
  return !Number.isNaN(date.getTime());
}

// Builds a UTC instant from wall-clock parts; null when the parts do not exist (e.g. month 13).
function wallClockToUtc(year: number, month: number, day: number, hour: number, minute: number, second = 0): Date | null {
  const date = new Date(Date.UTC(year, month - 1, day, hour, minute, second));
  const exists =
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day &&
    date.getUTCHours() === hour &&
    date.getUTCMinutes() === minute;
  return exists ? date : null;
}

interface Resolved {
  date: Date;
  wallClock: boolean;
}

function resolve(value: Date | string | null | undefined): Resolved | null {
  if (value == null) return null;

  if (value instanceof Date) {
    return isValid(value) ? { date: value, wallClock: false } : null;
  }

  // ISO-8601 without Z/offset (e.g. booking-service's LocalDateTime): no zone, shown as written.
  const naive = NAIVE_ISO.exec(value);
  if (naive) {
    const [, year, month, day, hour, minute, second] = naive;
    const date = wallClockToUtc(
      Number(year),
      Number(month),
      Number(day),
      Number(hour),
      Number(minute),
      second === undefined ? 0 : Number(second),
    );
    return date ? { date, wallClock: true } : null;
  }

  // ISO-8601 with Z/offset (or any other string the Date constructor understands).
  const date = new Date(value);
  return isValid(date) ? { date, wallClock: false } : null;
}

export function formatDateTime(value: Date | string | null | undefined): string {
  const resolved = resolve(value);
  if (!resolved) return INVALID;
  const formatter = resolved.wallClock ? wallClockDateTimeFormatter : zonedDateTimeFormatter;
  return formatter.format(resolved.date);
}

export function formatDate(value: Date | string | null | undefined): string {
  const resolved = resolve(value);
  if (!resolved) return INVALID;
  const formatter = resolved.wallClock ? wallClockDateFormatter : zonedDateFormatter;
  return formatter.format(resolved.date);
}

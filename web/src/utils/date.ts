import { parseBookingDateTime } from "../types/booking";

const TIME_ZONE = "America/Sao_Paulo";
const BOOKING_DATE_TIME = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/;
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

  // "dd/MM/yyyy HH:mm" from booking-service: no zone, reuse the existing parser.
  const booking = BOOKING_DATE_TIME.exec(value);
  if (booking) {
    const [, dd, mm, yyyy, HH, MM] = booking;
    const [day, month, year, hour, minute] = [dd, mm, yyyy, HH, MM].map(Number);
    const parsed = parseBookingDateTime(value);
    if (!isValid(parsed)) return null;
    // The parser builds a local Date and silently rolls over impossible parts (e.g. month 13),
    // so the components read back must match the ones written.
    if (
      parsed.getFullYear() !== year ||
      parsed.getMonth() + 1 !== month ||
      parsed.getDate() !== day ||
      parsed.getHours() !== hour ||
      parsed.getMinutes() !== minute
    ) {
      return null;
    }
    const date = wallClockToUtc(year, month, day, hour, minute);
    return date ? { date, wallClock: true } : null;
  }

  // ISO-8601 without Z/offset: no zone, shown as written.
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

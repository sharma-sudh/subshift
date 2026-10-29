const inr0 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inr2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 199 -> "₹199", 199.5 -> "₹199.50", null -> "—" */
export function formatMoney(value) {
  if (value === null || value === undefined || value === '' || Number.isNaN(Number(value))) return '—';
  const n = Number(value);
  return Number.isInteger(n) ? inr0.format(n) : inr2.format(n);
}

/** Everything the user sees is in India time. */
export const APP_TIME_ZONE = 'Asia/Kolkata';

const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;
const HAS_ZONE = /(Z|[+-]\d{2}:?\d{2})$/i;

/**
 * Parses an API value into a Date.
 * - "YYYY-MM-DD" is a calendar date: read as a LOCAL date (new Date("2026-01-15") would be UTC and can shift a day).
 * - A timestamp WITHOUT a zone (the API sends created_at, sent_at, ... like "2026-10-11T12:00:00") is stored in UTC,
 *   so a "Z" is added; browsers would otherwise read it as local time.
 * - A timestamp with a zone ("...Z" or "+05:30") is used as is.
 */
export function parseDate(value) {
  if (!value) return null;
  if (typeof value === 'string' && DATE_ONLY.test(value)) {
    const [y, m, d] = value.split('-').map(Number);
    return new Date(y, m - 1, d);
  }
  const text = typeof value === 'string' && !HAS_ZONE.test(value) ? `${value}Z` : value;
  const d = new Date(text);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** A calendar date such as "15 Jan 2026". Timestamps are shown as the date in India. */
export function formatDate(value) {
  const d = parseDate(value);
  if (!d) return '—';
  const timeZone = typeof value === 'string' && DATE_ONLY.test(value) ? undefined : APP_TIME_ZONE;
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone });
}

/** A timestamp in India time, e.g. "11 Oct 2026, 5:30 pm IST". */
export function formatDateTime(value) {
  if (typeof value === 'string' && DATE_ONLY.test(value)) return formatDate(value);
  const d = parseDate(value);
  if (!d) return '—';
  const text = d.toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', timeZone: APP_TIME_ZONE,
  });
  return `${text} IST`;
}

/** Today as "YYYY-MM-DD" in India time (matches the server's idea of today). */
export function todayISO() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: APP_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date());
}

export function daysSince(value) {
  const d = parseDate(value);
  if (!d) return null;
  return Math.round((parseDate(todayISO()) - d) / 86_400_000);
}

/** Percent change from old to new, e.g. 199 -> 249 = 25.1 */
export function percentChange(oldPrice, newPrice) {
  const o = Number(oldPrice);
  const n = Number(newPrice);
  if (!o || Number.isNaN(o) || Number.isNaN(n)) return null;
  return ((n - o) / o) * 100;
}

export function formatPercent(p) {
  if (p === null || p === undefined) return '';
  const sign = p > 0 ? '+' : '';
  return `${sign}${p.toFixed(1)}%`;
}

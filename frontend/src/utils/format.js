const inr0 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inr2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 199 -> "₹199", 199.5 -> "₹199.50", null -> "—" */
export function formatMoney(value) {
  if (value === null || value === undefined || value === '' || Number.isNaN(Number(value))) return '—';
  const n = Number(value);
  return Number.isInteger(n) ? inr0.format(n) : inr2.format(n);
}

/** Parses "YYYY-MM-DD" as a LOCAL date (new Date("2026-01-15") would be UTC and can shift a day). */
export function parseDate(value) {
  if (!value) return null;
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [y, m, d] = value.split('-').map(Number);
    return new Date(y, m - 1, d);
  }
  const d = new Date(value); // LocalDateTime strings like 2026-10-11T12:00:00 are read as local time
  return Number.isNaN(d.getTime()) ? null : d;
}

export function formatDate(value) {
  const d = parseDate(value);
  return d ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
}

export function formatDateTime(value) {
  const d = parseDate(value);
  return d
    ? d.toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })
    : '—';
}

/** Today as "YYYY-MM-DD" in the user's local timezone. */
export function todayISO() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function daysSince(value) {
  const d = parseDate(value);
  if (!d) return null;
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  return Math.floor((startOfToday - d) / 86_400_000);
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

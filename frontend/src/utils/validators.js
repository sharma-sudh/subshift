const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const isEmail = (v) => EMAIL_RE.test(String(v || '').trim());

/** True if the string is a positive number with at most 2 decimal places (matches DECIMAL(10,2)). */
export function isValidPrice(v) {
  const s = String(v ?? '').trim();
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return false;
  const n = Number(s);
  return n > 0 && n < 100_000_000;
}

export const hasErrors = (errors) => Object.values(errors).some(Boolean);

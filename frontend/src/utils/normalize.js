// The backend and the SQL view use slightly different field names (camelCase vs snake_case).
// Everything the UI renders goes through these functions so pages only deal with one shape.

const num = (v) => (v === null || v === undefined || v === '' || Number.isNaN(Number(v)) ? null : Number(v));

export function normalizeSubscription(r = {}) {
  return {
    id: r.id ?? r.subscriptionId ?? r.subscription_id,
    serviceId: r.serviceId ?? r.service_id ?? null,
    serviceName: r.serviceName ?? r.service_name ?? 'Unknown service',
    category: r.category ?? r.categoryName ?? r.category_name ?? 'Other',
    status: r.status ?? 'ACTIVE',
    currentPrice: num(r.currentPrice ?? r.current_price ?? r.latestPrice),
    lastCheckedDate: r.lastCheckedDate ?? r.last_checked_date ?? null,
    createdAt: r.createdAt ?? r.created_at ?? null,
  };
}

export function normalizeAlert(r = {}) {
  return {
    id: r.id,
    serviceName: r.serviceName ?? r.service_name ?? 'Unknown service',
    oldPrice: num(r.oldPrice ?? r.old_price),
    newPrice: num(r.newPrice ?? r.new_price),
    detectedAt: r.detectedAt ?? r.detected_at ?? null,
    isRead: Boolean(r.isRead ?? r.read ?? r.is_read),
  };
}

export function normalizeNotification(r = {}) {
  return {
    id: r.id,
    type: r.type,
    message: r.message ?? '',
    sentAt: r.sentAt ?? r.sent_at ?? null,
    isRead: Boolean(r.isRead ?? r.read ?? r.is_read),
  };
}

/** Sums the price of ACTIVE subscriptions per category. Used when the server summary isn't available. */
export function computeBreakdown(subs) {
  const byCat = new Map();
  for (const s of subs) {
    if (s.status !== 'ACTIVE' || s.currentPrice === null) continue;
    const row = byCat.get(s.category) || { category: s.category, amount: 0, count: 0 };
    row.amount += s.currentPrice;
    row.count += 1;
    byCat.set(s.category, row);
  }
  return [...byCat.values()].sort((a, b) => b.amount - a.amount);
}

export function normalizeSummary(raw = {}, fallbackSubs = []) {
  const subs = (raw.subscriptions ?? fallbackSubs).map(normalizeSubscription);
  const breakdown = (raw.categoryBreakdown ?? []).map((r) => ({
    category: r.category ?? r.categoryName ?? r.category_name,
    amount: num(r.amount ?? r.totalSpend ?? r.total_spend) ?? 0,
  }));
  const total = num(raw.totalMonthlySpend) ?? breakdown.reduce((sum, r) => sum + r.amount, 0);
  return { totalMonthlySpend: total, categoryBreakdown: breakdown, subscriptions: subs };
}

export function normalizeClusters(raw = {}) {
  return (raw.clusters ?? []).map((c) => ({
    category: c.category ?? c.name ?? 'Other',
    totalSpend: num(c.totalSpend ?? c.total_spend ?? c.amount) ?? 0,
    subscriptionCount: num(c.subscriptionCount ?? c.count) ?? 0,
  }));
}

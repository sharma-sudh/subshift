export function StatusBadge({ status }) {
  const active = status === 'ACTIVE';
  return <span className={`badge ${active ? 'badge-ok' : 'badge-muted'}`}>{active ? 'Active' : 'Cancelled'}</span>;
}

const TYPES = {
  PRICE_HIKE: { cls: 'badge-hike', icon: '▲', label: 'Price hike' },
  POSSIBLE_CANCELLATION: { cls: 'badge-cancel', icon: '◔', label: 'Possible cancellation' },
};

/** Different colour AND icon per notification type, so it isn't colour-only. */
export function TypeBadge({ type }) {
  const t = TYPES[type] || { cls: 'badge-muted', icon: '•', label: type || 'Notice' };
  return (
    <span className={`badge ${t.cls}`}>
      <span aria-hidden="true">{t.icon}</span> {t.label}
    </span>
  );
}

export function RoleBadge({ role }) {
  return <span className={`badge ${role === 'ADMIN' ? 'badge-admin' : 'badge-muted'}`}>{role}</span>;
}

export function StatusBadge({ status }) {
  const active = status === 'ACTIVE';
  return <span className={`badge ${active ? 'badge-ok' : 'badge-muted'}`}>{active ? 'Active' : 'Cancelled'}</span>;
}

export function RoleBadge({ role }) {
  return <span className={`badge ${role === 'ADMIN' ? 'badge-admin' : 'badge-muted'}`}>{role}</span>;
}

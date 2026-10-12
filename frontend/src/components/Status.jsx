export function Loading({ label = 'Loading…' }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorMessage({ error, onRetry }) {
  const message = typeof error === 'string' ? error : error?.message || 'Something went wrong.';
  return (
    <div className="state state-error" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

/** Inline message strip. kind: info | success | error | warn */
export function Banner({ kind = 'info', children, onClose }) {
  return (
    <div className={`banner banner-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <div className="banner-body">{children}</div>
      {onClose && (
        <button type="button" className="banner-close" aria-label="Dismiss message" onClick={onClose}>
          ×
        </button>
      )}
    </div>
  );
}

export function Empty({ title, children }) {
  return (
    <div className="panel empty">
      <h2>{title}</h2>
      {children}
    </div>
  );
}

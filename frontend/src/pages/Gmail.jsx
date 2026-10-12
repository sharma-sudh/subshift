import { useMemo, useState } from 'react';
import { api } from '../api';
import ConfirmDialog from '../components/ConfirmDialog';
import { Banner, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatDate, formatDateTime, formatMoney } from '../utils/format';

function formatScanPrice(price, currency) {
  if (price === null || price === undefined) return '—';
  if (!currency || currency === 'INR') return formatMoney(price);
  return `${currency} ${Number(price).toLocaleString('en-IN')}`;
}

export default function Gmail() {
  const status = useAsync(() => api.gmail.status(), []);
  const [message, setMessage] = useState(null);
  const [connecting, setConnecting] = useState(false);
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const [disconnecting, setDisconnecting] = useState(false);

  const [limit, setLimit] = useState(10);
  const [scan, setScan] = useState({ loading: false, items: null, error: '' });
  const [onlyRenewals, setOnlyRenewals] = useState(false);

  const connected = Boolean(status.data?.connected);

  const connect = async () => {
    setConnecting(true);
    setMessage(null);
    try {
      // The backend returns the Google consent URL as JSON (a redirect can't carry our auth header).
      const { authUrl } = await api.gmail.connect();
      window.location.assign(authUrl);
    } catch (e) {
      setMessage({ kind: 'error', text: e.message });
      setConnecting(false);
    }
  };

  const disconnect = async () => {
    setDisconnecting(true);
    try {
      await api.gmail.disconnect();
      setScan({ loading: false, items: null, error: '' });
      setMessage({ kind: 'success', text: 'Gmail disconnected.' });
      status.reload();
    } catch (e) {
      setMessage({ kind: 'error', text: e.message });
    } finally {
      setDisconnecting(false);
      setConfirmDisconnect(false);
    }
  };

  const runScan = async () => {
    setScan({ loading: true, items: null, error: '' });
    try {
      const items = await api.gmail.scan(limit);
      setScan({ loading: false, items: Array.isArray(items) ? items : [], error: '' });
    } catch (e) {
      setScan({ loading: false, items: null, error: e.message });
    }
  };

  const shown = useMemo(
    () => (scan.items ?? []).filter((i) => (onlyRenewals ? i.isRenewal : true)),
    [scan.items, onlyRenewals],
  );
  const renewalCount = (scan.items ?? []).filter((i) => i.isRenewal).length;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Gmail</h1>
          <p className="muted">SubShift reads your recent emails (read-only) to spot renewal and payment confirmations.</p>
        </div>
      </div>

      {message && (
        <Banner kind={message.kind} onClose={() => setMessage(null)}>
          {message.text}
        </Banner>
      )}

      <section className="panel" aria-labelledby="conn">
        <h2 id="conn">Connection</h2>
        {status.loading && !status.data && <Loading label="Checking connection…" />}
        {status.error && <ErrorMessage error={status.error} onRetry={status.reload} />}
        {status.data && (
          <div className="conn-row">
            <div>
              <p>
                <span className={`badge ${connected ? 'badge-ok' : 'badge-muted'}`}>{connected ? 'Connected' : 'Not connected'}</span>
              </p>
              {connected ? (
                <p className="muted small">
                  Connected {formatDateTime(status.data.connectedAt)}
                  {status.data.lastSyncedAt ? `, last synced ${formatDateTime(status.data.lastSyncedAt)}` : ''}.
                </p>
              ) : (
                <p className="muted small">We only ask for the gmail.readonly permission. We never send, delete or change email.</p>
              )}
            </div>
            {connected ? (
              <button type="button" className="btn btn-danger-outline" onClick={() => setConfirmDisconnect(true)}>
                Disconnect
              </button>
            ) : (
              <button type="button" className="btn btn-primary" onClick={connect} disabled={connecting}>
                {connecting ? 'Redirecting to Google…' : 'Connect Gmail'}
              </button>
            )}
          </div>
        )}
      </section>

      {connected && (
        <section className="panel" aria-labelledby="scan">
          <h2 id="scan">Scan inbox</h2>
          <p className="muted">
            A preview only: emails are fetched and analysed, but nothing is saved to your subscriptions yet.
          </p>
          <div className="scan-controls">
            <div className="field">
              <label htmlFor="limit">Emails to check</label>
              <select id="limit" value={limit} onChange={(e) => setLimit(Number(e.target.value))} disabled={scan.loading}>
                {[5, 10, 25].map((n) => (
                  <option key={n} value={n}>Latest {n}</option>
                ))}
              </select>
            </div>
            <button type="button" className="btn btn-primary" onClick={runScan} disabled={scan.loading}>
              {scan.loading ? 'Scanning…' : 'Scan now'}
            </button>
          </div>

          {scan.loading && <Loading label="Reading emails and extracting prices. This can take a little while…" />}
          {scan.error && <ErrorMessage error={scan.error} onRetry={runScan} />}

          {scan.items && (
            <>
              <div className="toolbar">
                <p className="muted" aria-live="polite">
                  {renewalCount} renewal email{renewalCount === 1 ? '' : 's'} found in {scan.items.length} checked.
                </p>
                <label className="check">
                  <input type="checkbox" checked={onlyRenewals} onChange={(e) => setOnlyRenewals(e.target.checked)} /> Renewals only
                </label>
              </div>
              {shown.length === 0 ? (
                <p className="empty-inline">{scan.items.length === 0 ? 'No emails were returned.' : 'No renewal emails in this batch.'}</p>
              ) : (
                <div className="table-wrap">
                  <table className="table table-stack">
                    <thead>
                      <tr>
                        <th scope="col">Email</th>
                        <th scope="col">Renewal?</th>
                        <th scope="col">Service</th>
                        <th scope="col" className="num">Price</th>
                        <th scope="col">Billing date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {shown.map((i) => (
                        <tr key={i.messageId}>
                          <td data-label="Email">
                            <div className="cell-title">{i.subject || '(no subject)'}</div>
                            <div className="muted small">{i.from} · {formatDate(i.emailDate)}</div>
                          </td>
                          <td data-label="Renewal?">
                            {i.error ? (
                              <span className="badge badge-hike" title={i.error}>Failed</span>
                            ) : (
                              <span className={`badge ${i.isRenewal ? 'badge-ok' : 'badge-muted'}`}>{i.isRenewal ? 'Yes' : 'No'}</span>
                            )}
                          </td>
                          <td data-label="Service">{i.serviceName || '—'}</td>
                          <td data-label="Price" className="num">{formatScanPrice(i.price, i.currency)}</td>
                          <td data-label="Billing date">{formatDate(i.billingDate)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </section>
      )}

      {confirmDisconnect && (
        <ConfirmDialog
          title="Disconnect Gmail?"
          message="SubShift will stop reading your email and revoke its access. Your tracked subscriptions and price history stay."
          confirmLabel="Disconnect"
          danger
          busy={disconnecting}
          onConfirm={disconnect}
          onCancel={() => setConfirmDisconnect(false)}
        />
      )}
    </div>
  );
}

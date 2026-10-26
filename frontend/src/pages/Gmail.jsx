import { useMemo, useState } from 'react';
import { api } from '../api';
import ConfirmDialog from '../components/ConfirmDialog';
import SyncPanel from '../components/SyncPanel';
import { Banner, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatDate, formatDateTime, formatMoney } from '../utils/format';

function formatScanPrice(price, currency) {
  if (price === null || price === undefined) return '—';
  if (!currency || currency === 'INR') return formatMoney(price);
  return `${currency} ${Number(price).toLocaleString('en-IN')}`;
}

/**
 * The email page (route /gmail, kept so existing links keep working). Connect Gmail, Outlook or Yahoo Mail, then
 * sync or scan. One mailbox per user: to switch provider, disconnect first.
 */
export default function Gmail() {
  const status = useAsync(() => api.mail.status(), []);
  const [message, setMessage] = useState(null);
  const [connecting, setConnecting] = useState('');
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const [disconnecting, setDisconnecting] = useState(false);

  const [yahoo, setYahoo] = useState({ email: '', appPassword: '' });

  const [limit, setLimit] = useState(10);
  const [scan, setScan] = useState({ loading: false, items: null, error: '' });

  const connected = Boolean(status.data?.connected);
  const providerLabel = status.data?.providerLabel || 'email';

  // Gmail and Outlook: the backend returns the consent URL as JSON (a redirect can't carry our auth header).
  const connectWithRedirect = async (provider) => {
    setConnecting(provider);
    setMessage(null);
    try {
      const { authUrl } = provider === 'gmail' ? await api.mail.connectGmail() : await api.mail.connectOutlook();
      window.location.assign(authUrl);
    } catch (e) {
      setMessage({ kind: 'error', text: e.message });
      setConnecting('');
    }
  };

  const connectYahoo = async (e) => {
    e.preventDefault();
    setConnecting('yahoo');
    setMessage(null);
    try {
      await api.mail.connectYahoo(yahoo.email, yahoo.appPassword);
      setYahoo({ email: '', appPassword: '' });
      setMessage({ kind: 'success', text: 'Yahoo Mail connected. You can now sync or scan your inbox.' });
      status.reload();
    } catch (err) {
      setMessage({ kind: 'error', text: err.message });
    } finally {
      setConnecting('');
    }
  };

  const disconnect = async () => {
    setDisconnecting(true);
    try {
      await api.mail.disconnect();
      setScan({ loading: false, items: null, error: '' });
      setMessage({ kind: 'success', text: `${providerLabel} disconnected.` });
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

  // Only real subscription renewals are shown; one-time purchases and other mail are not listed.
  const shown = useMemo(() => (scan.items ?? []).filter((i) => i.isRenewal && i.recurring && !i.error), [scan.items]);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Email</h1>
          <p className="muted">SubShift reads your recent emails (read-only) to spot subscription renewals and price changes.</p>
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

        {status.data && connected && (
          <div className="conn-row">
            <div>
              <p>
                <span className="badge badge-ok">Connected</span> <strong>{providerLabel}</strong>
                {status.data.mailboxAddress ? ` (${status.data.mailboxAddress})` : ''}
              </p>
              <p className="muted small">
                Connected {formatDateTime(status.data.connectedAt)}
                {status.data.lastSyncedAt ? `, last synced ${formatDateTime(status.data.lastSyncedAt)}` : ''}. To use a different
                mailbox, disconnect this one first.
              </p>
            </div>
            <button type="button" className="btn btn-danger-outline" onClick={() => setConfirmDisconnect(true)}>
              Disconnect
            </button>
          </div>
        )}

        {status.data && !connected && (
          <>
            <p>
              <span className="badge badge-muted">Not connected</span>
            </p>
            <p className="muted small">Choose where your subscription emails arrive. We only ask for read access: we never send, delete or change email.</p>
            <div className="provider-grid">
              <div className="provider-card">
                <h3>Gmail</h3>
                <p className="muted small">Sign in with Google (gmail.readonly permission).</p>
                <button type="button" className="btn btn-primary" onClick={() => connectWithRedirect('gmail')} disabled={Boolean(connecting)}>
                  {connecting === 'gmail' ? 'Redirecting to Google…' : 'Connect Gmail'}
                </button>
              </div>

              <div className="provider-card">
                <h3>Outlook</h3>
                <p className="muted small">Sign in with Microsoft (Outlook.com, Hotmail, or a work account; Mail.Read permission).</p>
                <button type="button" className="btn btn-primary" onClick={() => connectWithRedirect('outlook')} disabled={Boolean(connecting)}>
                  {connecting === 'outlook' ? 'Redirecting to Microsoft…' : 'Connect Outlook'}
                </button>
              </div>

              <form className="provider-card" onSubmit={connectYahoo}>
                <h3>Yahoo Mail</h3>
                <p className="muted small">
                  Yahoo needs an app password: in Yahoo Account Security choose &quot;Generate app password&quot;, then paste it here
                  (not your normal password).
                </p>
                <div className="field">
                  <label htmlFor="yahoo-email">Yahoo address</label>
                  <input
                    id="yahoo-email"
                    type="email"
                    autoComplete="email"
                    value={yahoo.email}
                    onChange={(e) => setYahoo({ ...yahoo, email: e.target.value })}
                    required
                  />
                </div>
                <div className="field">
                  <label htmlFor="yahoo-pass">App password</label>
                  <input
                    id="yahoo-pass"
                    type="password"
                    autoComplete="off"
                    value={yahoo.appPassword}
                    onChange={(e) => setYahoo({ ...yahoo, appPassword: e.target.value })}
                    required
                  />
                </div>
                <button type="submit" className="btn btn-primary" disabled={Boolean(connecting)}>
                  {connecting === 'yahoo' ? 'Checking with Yahoo…' : 'Connect Yahoo Mail'}
                </button>
              </form>
            </div>
          </>
        )}
      </section>

      {connected && <SyncPanel onSynced={status.reload} />}

      {connected && (
        <section className="panel" aria-labelledby="scan">
          <h2 id="scan">Scan inbox</h2>
          <p className="muted">
            A preview only: emails are fetched and analysed, but nothing is saved to your subscriptions yet. Only subscription
            renewals are listed.
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
                  {shown.length} subscription email{shown.length === 1 ? '' : 's'} found in {scan.items.length} checked.
                </p>
              </div>
              {shown.length === 0 ? (
                <p className="empty-inline">{scan.items.length === 0 ? 'No emails were returned.' : 'No subscription emails in this batch.'}</p>
              ) : (
                <div className="table-wrap">
                  <table className="table table-stack">
                    <thead>
                      <tr>
                        <th scope="col">Email</th>
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
                          <td data-label="Service">
                            {i.serviceName || '—'}
                            {i.planName && <span className="muted small"> · {i.planName}</span>}
                          </td>
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
          title={`Disconnect ${providerLabel}?`}
          message="SubShift will stop reading your email and remove its access. Your tracked subscriptions and price history stay."
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

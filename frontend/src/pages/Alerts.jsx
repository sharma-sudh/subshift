import { useMemo, useState } from 'react';
import { api } from '../api';
import { TypeBadge } from '../components/Badges';
import { Banner, Empty, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime, formatMoney, formatPercent, percentChange } from '../utils/format';

export default function Alerts() {
  const [tab, setTab] = useState('changes');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [message, setMessage] = useState(null);

  const alerts = useAsync(() => api.alerts.list(), []);
  const notes = useAsync(() => api.notifications.list(), []);

  const current = tab === 'changes' ? alerts : notes;
  const markReadFn = tab === 'changes' ? api.alerts.markRead : api.notifications.markRead;

  const items = useMemo(() => {
    const all = current.data ?? [];
    return unreadOnly ? all.filter((i) => !i.isRead) : all;
  }, [current.data, unreadOnly]);

  const unreadAlerts = (alerts.data ?? []).filter((a) => !a.isRead).length;
  const unreadNotes = (notes.data ?? []).filter((n) => !n.isRead).length;
  const unreadInTab = (current.data ?? []).filter((i) => !i.isRead).length;

  const markRead = async (id) => {
    try {
      const updated = await markReadFn(id);
      current.setData((list) => (list ?? []).map((i) => (i.id === id ? { ...i, ...updated, isRead: true } : i)));
    } catch (e) {
      setMessage({ kind: 'error', text: e.message });
    }
  };

  const markAllRead = async () => {
    const unread = (current.data ?? []).filter((i) => !i.isRead);
    setMessage(null);
    const results = await Promise.allSettled(unread.map((i) => markReadFn(i.id)));
    const okIds = new Set(unread.filter((_, idx) => results[idx].status === 'fulfilled').map((i) => i.id));
    current.setData((list) => (list ?? []).map((i) => (okIds.has(i.id) ? { ...i, isRead: true } : i)));
    if (okIds.size < unread.length) setMessage({ kind: 'error', text: 'Some items could not be marked as read. Please try again.' });
  };

  // The notifications endpoint is built separately; a 404 means it isn't deployed yet.
  const notesUnavailable = tab === 'notes' && notes.error?.status === 404;

  return (
    <div className="page narrow">
      <div className="page-head">
        <div>
          <h1>Alerts</h1>
          <p className="muted">Price increases, price drops and possible cancellations we noticed.</p>
        </div>
      </div>

      <EmailAlertsSetting />

      <div className="tabs" role="tablist" aria-label="Alert types">
        <button type="button" role="tab" aria-selected={tab === 'changes'} className={`tab${tab === 'changes' ? ' tab-active' : ''}`} onClick={() => setTab('changes')}>
          Price changes {unreadAlerts > 0 && <span className="count-pill">{unreadAlerts}</span>}
        </button>
        <button type="button" role="tab" aria-selected={tab === 'notes'} className={`tab${tab === 'notes' ? ' tab-active' : ''}`} onClick={() => setTab('notes')}>
          Notifications {unreadNotes > 0 && <span className="count-pill">{unreadNotes}</span>}
        </button>
      </div>

      {message && (
        <Banner kind={message.kind} onClose={() => setMessage(null)}>
          {message.text}
        </Banner>
      )}

      {current.loading && !current.data && <Loading label="Loading…" />}
      {notesUnavailable && (
        <Banner kind="info">Notifications aren’t available on this server yet. Price-change alerts are in the other tab.</Banner>
      )}
      {current.error && !notesUnavailable && <ErrorMessage error={current.error} onRetry={current.reload} />}

      {current.data && (
        <>
          <div className="toolbar">
            <label className="check">
              <input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} /> Unread only
            </label>
            {unreadInTab > 0 && (
              <button type="button" className="btn btn-secondary btn-sm" onClick={markAllRead}>
                Mark all as read
              </button>
            )}
          </div>

          {items.length === 0 ? (
            <Empty title={unreadOnly ? 'No unread items' : tab === 'changes' ? 'No price changes detected' : 'No notifications yet'}>
              <p>{unreadOnly ? 'You are all caught up.' : 'When something changes, it will show up here.'}</p>
            </Empty>
          ) : (
            <ul className="alert-list">
              {items.map((i) =>
                tab === 'changes' ? <ChangeItem key={i.id} alert={i} onRead={markRead} /> : <NoteItem key={i.id} note={i} onRead={markRead} />,
              )}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

/** Switch for emailing alerts to the account address (SMTP), plus a button to check the mail setup. */
function EmailAlertsSetting() {
  const pref = useAsync(() => api.notifications.emailPreference(), []);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState(null);

  // An older backend without the endpoint just hides this panel.
  if (pref.error || pref.data === undefined || pref.data === null) return null;

  const toggle = async (enabled) => {
    setBusy(true);
    setNote(null);
    try {
      pref.setData(await api.notifications.setEmailPreference(enabled));
    } catch (e) {
      setNote({ kind: 'error', text: e.message });
    } finally {
      setBusy(false);
    }
  };

  const sendTest = async () => {
    setBusy(true);
    setNote(null);
    try {
      await api.notifications.sendTestEmail();
      setNote({ kind: 'success', text: 'Test email sent to your account address. Check your inbox (and spam).' });
    } catch (e) {
      setNote({ kind: 'error', text: e.message });
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel" aria-labelledby="email-alerts">
      <h2 id="email-alerts">Email alerts</h2>
      <div className="toolbar">
        <label className="check">
          <input type="checkbox" checked={pref.data} disabled={busy} onChange={(e) => toggle(e.target.checked)} /> Also email me
          price changes and possible cancellations
        </label>
        <button type="button" className="btn btn-secondary btn-sm" onClick={sendTest} disabled={busy}>
          Send test email
        </button>
      </div>
      {note && (
        <Banner kind={note.kind} onClose={() => setNote(null)}>
          {note.text}
        </Banner>
      )}
    </section>
  );
}

function ChangeItem({ alert, onRead }) {
  const pct = percentChange(alert.oldPrice, alert.newPrice);
  const dropped = Number(alert.newPrice) < Number(alert.oldPrice);
  const tone = dropped ? 'delta-down' : 'delta-up';
  return (
    <li className={`alert-item${alert.isRead ? ' is-read' : ''}`}>
      <div className="alert-main">
        <div className="alert-title">
          {!alert.isRead && <span className="unread-dot" aria-label="Unread" />}
          <TypeBadge type={dropped ? 'PRICE_DROP' : 'PRICE_HIKE'} />
          <strong>{alert.serviceName}</strong>
        </div>
        <p className="alert-price">
          <span className="old">{formatMoney(alert.oldPrice)}</span>
          <span aria-label="to"> → </span>
          <span className={`new ${tone}`}>{formatMoney(alert.newPrice)}</span>
          {pct !== null && <span className={`delta ${tone}`}> {formatPercent(pct)}</span>}
        </p>
        <p className="muted small">Detected {formatDateTime(alert.detectedAt)}</p>
      </div>
      {!alert.isRead && (
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => onRead(alert.id)}>
          Mark as read
        </button>
      )}
    </li>
  );
}

function NoteItem({ note, onRead }) {
  return (
    <li className={`alert-item${note.isRead ? ' is-read' : ''}`}>
      <div className="alert-main">
        <div className="alert-title">
          {!note.isRead && <span className="unread-dot" aria-label="Unread" />}
          <TypeBadge type={note.type} />
        </div>
        <p>{note.message}</p>
        <p className="muted small">{formatDateTime(note.sentAt)}</p>
      </div>
      {!note.isRead && (
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => onRead(note.id)}>
          Mark as read
        </button>
      )}
    </li>
  );
}

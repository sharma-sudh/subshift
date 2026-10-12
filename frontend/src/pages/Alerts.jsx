import { api } from '../api';
import { Empty, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime, formatMoney } from '../utils/format';

export default function Alerts() {
  const { data, error, loading, reload, setData } = useAsync(() => api.alerts.list(), []);

  const markRead = async (id) => {
    try {
      await api.alerts.markRead(id);
      setData((list) => (list ?? []).map((a) => (a.id === id ? { ...a, isRead: true } : a)));
    } catch (e) {
      window.alert(e.message);
    }
  };

  return (
    <div className="page narrow">
      <h1>Alerts</h1>
      {loading && !data && <Loading />}
      {error && <ErrorMessage error={error} onRetry={reload} />}
      {data && data.length === 0 && <Empty title="No price hikes detected" />}
      {data && data.length > 0 && (
        <ul className="alert-list">
          {data.map((a) => (
            <li key={a.id} className={`alert-item${a.isRead ? ' is-read' : ''}`}>
              <div className="alert-main">
                <strong>{a.serviceName}</strong>
                <p className="alert-price">
                  <span className="old">{formatMoney(a.oldPrice)}</span> → <span className="new">{formatMoney(a.newPrice)}</span>
                </p>
                <p className="muted small">Detected {formatDateTime(a.detectedAt)}</p>
              </div>
              {!a.isRead && (
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => markRead(a.id)}>
                  Mark as read
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

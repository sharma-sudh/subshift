import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { formatDate, formatMoney, formatPercent, percentChange } from '../utils/format';
import { ErrorMessage, Loading } from './Status';

/** Line chart of a subscription's price over time. price_log only stores *changes*, so we draw a step line. */
export default function PriceHistory({ subscriptionId }) {
  const { data, error, loading, reload } = useAsync(() => api.subscriptions.priceHistory(subscriptionId), [subscriptionId]);

  if (loading && !data) return <Loading label="Loading price history…" />;
  if (error) return <ErrorMessage error={error} onRetry={reload} />;
  if (!data || data.length === 0) {
    return <p className="muted">No price history recorded yet. Add a price with “Add subscription”, or scan your Gmail.</p>;
  }

  const points = data.map((p) => ({ ...p, label: formatDate(p.dateRecorded) }));
  const first = points[0];
  const last = points[points.length - 1];
  const change = percentChange(first.price, last.price);

  return (
    <div>
      <p className="history-summary">
        {points.length === 1 ? (
          <>Only one price recorded so far: <strong>{formatMoney(last.price)}</strong> on {last.label}.</>
        ) : (
          <>
            {formatMoney(first.price)} on {first.label} → <strong>{formatMoney(last.price)}</strong> on {last.label}
            {change !== null && change !== 0 && (
              <span className={change > 0 ? 'delta delta-up' : 'delta delta-down'}> ({formatPercent(change)})</span>
            )}
          </>
        )}
      </p>
      <div className="chart-box" style={{ height: 240 }} role="img" aria-label="Price history line chart">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={points} margin={{ top: 8, right: 16, bottom: 4, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#dfe6e4" />
            <XAxis dataKey="label" tick={{ fontSize: 12, fill: '#4b5b6b' }} />
            <YAxis tickFormatter={(v) => formatMoney(v)} tick={{ fontSize: 12, fill: '#4b5b6b' }} width={64} domain={['auto', 'auto']} />
            <Tooltip formatter={(v) => [formatMoney(v), 'Price']} />
            <Line type="stepAfter" dataKey="price" stroke="#0f766e" strokeWidth={2.5} dot={{ r: 4, fill: '#0f766e' }} activeDot={{ r: 6 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

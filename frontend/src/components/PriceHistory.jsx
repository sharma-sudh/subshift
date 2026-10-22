import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { formatDate, formatMoney, formatPercent, percentChange } from '../utils/format';
import { ErrorMessage, Loading } from './Status';

/**
 * Predicted next price change (GET /api/subscriptions/{id}/forecast).
 * Needs at least 2 recorded prices; otherwise the server returns predictedNextPrice = null and a `note`.
 * A forecast failure is shown inline and never blocks the price chart.
 */
function Forecast({ subscriptionId }) {
  const { data, error, loading, reload } = useAsync(() => api.subscriptions.forecast(subscriptionId), [subscriptionId]);

  if (loading && !data) return <Loading label="Predicting next price…" />;
  if (error) return <ErrorMessage error={error} onRetry={reload} />;
  if (!data) return null;

  if (data.predictedNextPrice === null) {
    return (
      <div className="forecast forecast-empty">
        <h3>Price forecast</h3>
        <p className="muted">{data.note || 'A forecast needs at least two recorded prices.'}</p>
      </div>
    );
  }

  const pct = data.expectedIncreasePercent;
  return (
    <div className="forecast">
      <h3>Price forecast</h3>
      <p>
        Next change expected around <strong>{data.expectedChangeDate ? formatDate(data.expectedChangeDate) : 'a date we can’t estimate yet'}</strong>:{' '}
        {formatMoney(data.currentPrice)} to <strong>{formatMoney(data.predictedNextPrice)}</strong>
        {pct !== null && pct !== undefined && (
          <span className={pct > 0 ? 'delta delta-up' : 'delta delta-down'}> ({formatPercent(pct)})</span>
        )}
      </p>
      {data.note && <p className="muted small">{data.note}</p>}
    </div>
  );
}

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
      <Forecast subscriptionId={subscriptionId} />
    </div>
  );
}

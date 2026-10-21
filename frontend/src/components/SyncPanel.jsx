import { useState } from 'react';
import { api } from '../api';
import { formatDateTime } from '../utils/format';
import { Banner } from './Status';

const EMPTY_TOTALS = {
  emailsProcessed: 0,
  pricesRecorded: 0,
  hikesNotified: 0,
  newSubscriptions: 0,
  newServices: 0,
};

/** Human wording for the `result` strings the sync pipeline returns per email. */
const RESULT_LABELS = {
  price_recorded: 'Price recorded',
  price_hike_notified: 'Price hike detected',
  unchanged: 'Price unchanged',
  skipped_by_classifier: 'Not a renewal (classifier)',
  skipped_not_renewal: 'Not a renewal',
  skipped_payment_failed: 'Payment failed',
  skipped_not_recurring: 'Not recurring',
  skipped_no_price: 'No price found',
  skipped_currency: 'Unsupported currency',
  skipped_no_service_name: 'No service name',
  skipped_invalid_service_name: 'Invalid service name',
  skipped_new_service_limit: 'New-service limit reached',
  skipped_no_other_category: 'No category available',
  skipped_older_than_current: 'Older than current price',
};
const resultLabel = (r) => RESULT_LABELS[r] || String(r || '').replace(/_/g, ' ') || '—';
const resultKind = (r) => (r === 'price_hike_notified' ? 'badge-hike' : r === 'price_recorded' ? 'badge-ok' : 'badge-muted');

const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

/**
 * "Sync now" for the Gmail page: POST /api/email/sync?limit=N.
 * Totals are added up across runs; "Sync more" appears while emailsRemaining > 0.
 * `onSynced` is called after every successful run so the parent can refresh GET /api/gmail/status.
 */
export default function SyncPanel({ onSynced }) {
  const [limit, setLimit] = useState(10);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null); // { totals, outcomes, runs, remaining, stoppedEarly, stopReason, lastSyncedAt }

  const run = async (continuing) => {
    setSyncing(true);
    setError('');
    try {
      const r = await api.gmail.sync(limit);
      setResult((prev) => {
        const base = continuing && prev ? prev : { totals: { ...EMPTY_TOTALS }, outcomes: [], runs: 0 };
        const totals = { ...base.totals };
        for (const key of Object.keys(EMPTY_TOTALS)) totals[key] += Number(r[key]) || 0;
        return {
          totals,
          outcomes: [...base.outcomes, ...(Array.isArray(r.outcomes) ? r.outcomes : [])],
          runs: base.runs + 1,
          remaining: Number(r.emailsRemaining) || 0,
          stoppedEarly: Boolean(r.stoppedEarly),
          stopReason: r.stopReason || '',
          lastSyncedAt: r.lastSyncedAt,
        };
      });
      onSynced?.();
    } catch (e) {
      setError(e.message);
    } finally {
      setSyncing(false);
    }
  };

  const t = result?.totals;
  const interesting = (result?.outcomes ?? []).filter((o) => o.result === 'price_recorded' || o.result === 'price_hike_notified');

  return (
    <section className="panel" aria-labelledby="sync">
      <h2 id="sync">Sync inbox</h2>
      <p className="muted">
        Reads new emails and saves what it finds: prices, price-hike alerts and new subscriptions. Unlike a scan, this
        changes your data.
      </p>
      <div className="scan-controls">
        <div className="field">
          <label htmlFor="sync-limit">Emails per run</label>
          <select id="sync-limit" value={limit} onChange={(e) => setLimit(Number(e.target.value))} disabled={syncing}>
            {[5, 10, 25].map((n) => (
              <option key={n} value={n}>Up to {n}</option>
            ))}
          </select>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => run(false)} disabled={syncing} aria-busy={syncing}>
          {syncing ? (
            <>
              <span className="spinner spinner-inline" aria-hidden="true" /> Syncing…
            </>
          ) : (
            'Sync now'
          )}
        </button>
      </div>

      {syncing && (
        <div className="state" role="status" aria-live="polite">
          <span className="spinner" aria-hidden="true" />
          <span>Reading emails and extracting prices. This can take a minute, please keep this page open…</span>
        </div>
      )}

      {error && <Banner kind="error" onClose={() => setError('')}>{error}</Banner>}

      {result && !syncing && (
        <div className="sync-summary" aria-live="polite">
          {result.stoppedEarly && (
            <Banner kind="warn">
              Sync stopped early{result.stopReason ? `: ${result.stopReason}` : '.'}
            </Banner>
          )}

          <p className="sync-headline">
            {t.emailsProcessed === 0 && result.runs === 1
              ? 'No new emails to process.'
              : `Processed ${plural(t.emailsProcessed, 'email')}${result.runs > 1 ? ` in ${result.runs} runs` : ''}.`}
          </p>

          <dl className="sync-stats">
            <div><dt>Prices recorded</dt><dd>{t.pricesRecorded}</dd></div>
            <div><dt>Price hikes</dt><dd>{t.hikesNotified}</dd></div>
            <div><dt>New subscriptions</dt><dd>{t.newSubscriptions}</dd></div>
            <div><dt>New services</dt><dd>{t.newServices}</dd></div>
          </dl>

          {result.lastSyncedAt && <p className="muted small">Last synced {formatDateTime(result.lastSyncedAt)}.</p>}

          {result.remaining > 0 && (
            <div className="sync-more">
              <p className="muted">{plural(result.remaining, 'email')} still waiting to be processed.</p>
              <button type="button" className="btn btn-secondary" onClick={() => run(true)} disabled={syncing}>
                {result.stoppedEarly ? 'Try again' : 'Sync more'}
              </button>
            </div>
          )}
          {result.remaining === 0 && !result.stoppedEarly && t.emailsProcessed > 0 && (
            <p className="muted small">All caught up.</p>
          )}

          {result.outcomes.length > 0 && (
            <details className="sync-details">
              <summary>
                Details ({plural(result.outcomes.length, 'email')}
                {interesting.length > 0 ? `, ${interesting.length} saved` : ''})
              </summary>
              <ul className="plain-list">
                {result.outcomes.map((o, i) => (
                  <li key={`${o.messageId}-${i}`} className="sync-outcome">
                    <span className="sync-outcome-text">
                      <span className="cell-title">{o.serviceName || o.subject || '(no subject)'}</span>
                      {o.serviceName && o.subject && <span className="muted small"> {o.subject}</span>}
                    </span>
                    <span className={`badge ${resultKind(o.result)}`}>{resultLabel(o.result)}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
    </section>
  );
}

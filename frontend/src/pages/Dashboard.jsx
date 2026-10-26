import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth/AuthContext';
import { StatusBadge } from '../components/Badges';
import { CategoryBarChart } from '../components/Charts';
import { Banner, Empty, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatMoney } from '../utils/format';
import { mailReasonText } from '../utils/gmailReasons';

export default function Dashboard() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const [gmailNotice, setGmailNotice] = useState(null);

  // Provider -> backend -> here: /dashboard?gmail=connected, ?mail=connected&provider=outlook, or ?...=error&reason=...
  useEffect(() => {
    const result = params.get('gmail') ?? params.get('mail');
    if (!result) return;
    const provider = params.get('mail') ? params.get('provider') : 'gmail';
    const name = provider === 'outlook' ? 'Outlook' : provider === 'gmail' ? 'Gmail' : 'Email';
    setGmailNotice(
      result === 'connected'
        ? { kind: 'success', text: `${name} connected. You can now scan your inbox for subscription emails.` }
        : { kind: 'error', text: mailReasonText(params.get('reason')) },
    );
    setParams({}, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const { data, error, loading, reload } = useAsync(async () => {
    const [summary, alerts] = await Promise.all([api.dashboard.summary(), api.alerts.list().catch(() => [])]);
    return { summary, unreadAlerts: alerts.filter((a) => !a.isRead).length };
  }, []);

  const subs = data?.summary.subscriptions ?? [];
  const activeCount = subs.filter((s) => s.status === 'ACTIVE').length;
  const topSubs = useMemo(
    () =>
      subs
        .filter((s) => s.status === 'ACTIVE')
        .sort((a, b) => (b.currentPrice ?? -1) - (a.currentPrice ?? -1))
        .slice(0, 5),
    [subs],
  );

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>SubShift Dashboard</h1>
          <p className="muted">Welcome back, {user.name}.</p>
        </div>
        <Link to="/manual-entry" className="btn btn-primary">
          Add subscription
        </Link>
      </div>

      {gmailNotice && (
        <Banner kind={gmailNotice.kind} onClose={() => setGmailNotice(null)}>
          {gmailNotice.text} {gmailNotice.kind === 'success' && <Link to="/gmail">Go to email scan</Link>}
        </Banner>
      )}

      {loading && !data && <Loading label="Loading your dashboard…" />}
      {error && <ErrorMessage error={error} onRetry={reload} />}

      {data && (
        <>
          {data.summary.source === 'estimated' && (
            <Banner kind="info">
              Showing totals calculated from your subscription list. They will switch to the server-side summary once it is available.
            </Banner>
          )}

          <section className="stats" aria-label="Summary">
            <div className="stat stat-primary">
              <span className="stat-label">Monthly spend</span>
              <span className="stat-value">{formatMoney(data.summary.totalMonthlySpend)}</span>
              <span className="stat-note">across active subscriptions</span>
            </div>
            <div className="stat">
              <span className="stat-label">Active subscriptions</span>
              <span className="stat-value">{activeCount}</span>
              <Link to="/subscriptions" className="stat-note">View all</Link>
            </div>
            <div className={`stat${data.unreadAlerts > 0 ? ' stat-warn' : ''}`}>
              <span className="stat-label">Unread price-change alerts</span>
              <span className="stat-value">{data.unreadAlerts}</span>
              <Link to="/alerts" className="stat-note">{data.unreadAlerts > 0 ? 'Review now' : 'Open alerts'}</Link>
            </div>
          </section>

          {subs.length === 0 ? (
            <Empty title="No subscriptions yet">
              <p>Add one manually, or connect your email and let SubShift find your renewal emails.</p>
              <div className="row-actions center">
                <Link to="/manual-entry" className="btn btn-primary">Add a subscription</Link>
                <Link to="/gmail" className="btn btn-secondary">Connect email</Link>
              </div>
            </Empty>
          ) : (
            <div className="grid-2">
              <section className="panel" aria-labelledby="by-cat">
                <h2 id="by-cat">Spend by category</h2>
                {data.summary.categoryBreakdown.length > 0 ? (
                  <CategoryBarChart data={data.summary.categoryBreakdown} />
                ) : (
                  <p className="muted">No priced active subscriptions yet.</p>
                )}
              </section>

              <section className="panel" aria-labelledby="top-subs">
                <div className="panel-head">
                  <h2 id="top-subs">Biggest active subscriptions</h2>
                  <Link to="/subscriptions">See all</Link>
                </div>
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th scope="col">Service</th>
                        <th scope="col">Category</th>
                        <th scope="col" className="num">Price</th>
                        <th scope="col">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {topSubs.map((s) => (
                        <tr key={s.id}>
                          <td>{s.serviceName}{s.planName && <span className="muted small"> · {s.planName}</span>}</td>
                          <td>{s.category}</td>
                          <td className="num">{formatMoney(s.currentPrice)}</td>
                          <td><StatusBadge status={s.status} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </div>
          )}
        </>
      )}
    </div>
  );
}

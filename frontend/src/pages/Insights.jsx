import { Link } from 'react-router-dom';
import { api } from '../api';
import { CategoryBarChart, CategoryPieChart } from '../components/Charts';
import { Banner, Empty, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatMoney } from '../utils/format';

export default function Insights() {
  const { data, error, loading, reload } = useAsync(() => api.dashboard.insights(), []);
  const clusters = [...(data?.clusters ?? [])].sort((a, b) => b.totalSpend - a.totalSpend);
  const total = clusters.reduce((sum, c) => sum + c.totalSpend, 0);
  const top = clusters[0];

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Spend insights</h1>
          <p className="muted">Where your monthly subscription money goes.</p>
        </div>
      </div>

      {loading && !data && <Loading label="Crunching your spend…" />}
      {error && <ErrorMessage error={error} onRetry={reload} />}

      {data && data.source === 'estimated' && (
        <Banner kind="info">These figures are grouped by category in your browser. Clustering insights from the server will appear here once that endpoint is live.</Banner>
      )}

      {data && clusters.length === 0 && (
        <Empty title="Nothing to analyse yet">
          <p>Add a few subscriptions with prices to see how your spend is distributed.</p>
          <Link to="/manual-entry" className="btn btn-primary">Add a subscription</Link>
        </Empty>
      )}

      {data && clusters.length > 0 && (
        <>
          {top && (
            <p className="insight-summary">
              You spend the most on <strong>{top.category}</strong> ({formatMoney(top.totalSpend)}/month across {top.subscriptionCount}{' '}
              subscription{top.subscriptionCount === 1 ? '' : 's'}), which is {total > 0 ? Math.round((top.totalSpend / total) * 100) : 0}% of your{' '}
              {formatMoney(total)} total.
            </p>
          )}

          <div className="grid-2">
            <section className="panel" aria-labelledby="share">
              <h2 id="share">Share of spend</h2>
              <CategoryPieChart data={clusters} />
            </section>
            <section className="panel" aria-labelledby="amounts">
              <h2 id="amounts">Spend per category</h2>
              <CategoryBarChart data={clusters.map((c) => ({ category: c.category, amount: c.totalSpend }))} />
            </section>
          </div>

          <section className="panel">
            <h2>Breakdown</h2>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Category</th>
                    <th scope="col" className="num">Subscriptions</th>
                    <th scope="col" className="num">Per month</th>
                    <th scope="col" className="num">Share</th>
                  </tr>
                </thead>
                <tbody>
                  {clusters.map((c) => (
                    <tr key={c.category}>
                      <td>{c.category}</td>
                      <td className="num">{c.subscriptionCount}</td>
                      <td className="num">{formatMoney(c.totalSpend)}</td>
                      <td className="num">{total > 0 ? Math.round((c.totalSpend / total) * 100) : 0}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  );
}

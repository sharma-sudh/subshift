import { api } from '../api';
import { StatusBadge } from '../components/Badges';
import { Empty, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatMoney } from '../utils/format';

export default function Subscriptions() {
  const { data, error, loading, reload } = useAsync(() => api.subscriptions.list(), []);

  return (
    <div className="page">
      <h1>Subscriptions</h1>
      {loading && !data && <Loading label="Loading subscriptions…" />}
      {error && <ErrorMessage error={error} onRetry={reload} />}
      {data && data.length === 0 && <Empty title="No subscriptions yet" />}
      {data && data.length > 0 && (
        <section className="panel">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Service Name</th>
                  <th scope="col">Category</th>
                  <th scope="col" className="num">Current Price</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                {data.map((s) => (
                  <tr key={s.id}>
                    <td>{s.serviceName}</td>
                    <td>{s.category}</td>
                    <td className="num">{formatMoney(s.currentPrice)}</td>
                    <td><StatusBadge status={s.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

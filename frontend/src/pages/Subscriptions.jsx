import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import { StatusBadge } from '../components/Badges';
import Pagination from '../components/Pagination';
import { Empty, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatMoney } from '../utils/format';

const PAGE_SIZE = 10;

export default function Subscriptions() {
  const { data, error, loading, reload } = useAsync(() => api.subscriptions.list(), []);
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('all');
  const [page, setPage] = useState(1);

  const subs = data ?? [];
  const categories = useMemo(() => [...new Set(subs.map((s) => s.category))].sort(), [subs]);
  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return subs
      .filter((s) => (needle ? s.serviceName.toLowerCase().includes(needle) : true))
      .filter((s) => (category === 'all' ? true : s.category === category))
      .sort((a, b) => a.serviceName.localeCompare(b.serviceName));
  }, [subs, q, category]);

  useEffect(() => setPage(1), [q, category]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  return (
    <div className="page">
      <h1>Subscriptions</h1>
      {loading && !data && <Loading label="Loading subscriptions…" />}
      {error && <ErrorMessage error={error} onRetry={reload} />}
      {data && subs.length === 0 && <Empty title="No subscriptions yet" />}
      {data && subs.length > 0 && (
        <section className="panel">
          <form className="filters" role="search" onSubmit={(e) => e.preventDefault()}>
            <div className="field">
              <label htmlFor="q">Search service</label>
              <input id="q" type="search" value={q} onChange={(e) => setQ(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="category">Category</label>
              <select id="category" value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="all">All categories</option>
                {categories.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
          </form>
          {filtered.length === 0 ? (
            <p className="empty-inline">No subscriptions match your filters.</p>
          ) : (
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
                  {pageItems.map((s) => (
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
          )}
          <Pagination page={currentPage} pageSize={PAGE_SIZE} total={filtered.length} onChange={setPage} />
        </section>
      )}
    </div>
  );
}

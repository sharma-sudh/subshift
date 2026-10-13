import { Fragment, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { StatusBadge } from '../components/Badges';
import ConfirmDialog from '../components/ConfirmDialog';
import Pagination from '../components/Pagination';
import PriceHistory from '../components/PriceHistory';
import { Banner, Empty, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { daysSince, formatDate, formatMoney } from '../utils/format';

const PAGE_SIZE = 10;
const STALE_DAYS = 60; // matches the "possible cancellation" rule in the design spec

const SORTS = {
  name: (a, b) => a.serviceName.localeCompare(b.serviceName),
  'price-desc': (a, b) => (b.currentPrice ?? -1) - (a.currentPrice ?? -1),
  'price-asc': (a, b) => (a.currentPrice ?? Infinity) - (b.currentPrice ?? Infinity),
  checked: (a, b) => String(b.lastCheckedDate ?? '').localeCompare(String(a.lastCheckedDate ?? '')),
};

export default function Subscriptions() {
  const { data, error, loading, reload } = useAsync(() => api.subscriptions.list(), []);

  const [q, setQ] = useState('');
  const [category, setCategory] = useState('all');
  const [status, setStatus] = useState('all');
  const [sort, setSort] = useState('name');
  const [page, setPage] = useState(1);

  const [expandedId, setExpandedId] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [toDelete, setToDelete] = useState(null);
  const [message, setMessage] = useState(null); // { kind, text }

  const subs = data ?? [];
  const categories = useMemo(() => [...new Set(subs.map((s) => s.category))].sort(), [subs]);

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return subs
      .filter((s) => (needle ? s.serviceName.toLowerCase().includes(needle) : true))
      .filter((s) => (category === 'all' ? true : s.category === category))
      .filter((s) => (status === 'all' ? true : s.status === status))
      .sort(SORTS[sort]);
  }, [subs, q, category, status, sort]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  // Any filter change returns to page 1.
  useEffect(() => setPage(1), [q, category, status, sort]);

  const filtersActive = q.trim() !== '' || category !== 'all' || status !== 'all';
  const clearFilters = () => {
    setQ('');
    setCategory('all');
    setStatus('all');
  };

  const toggleStatus = async (s) => {
    const next = s.status === 'ACTIVE' ? 'CANCELLED' : 'ACTIVE';
    setBusyId(s.id);
    setMessage(null);
    try {
      await api.subscriptions.setStatus(s.id, next);
      setMessage({ kind: 'success', text: `${s.serviceName} marked as ${next === 'ACTIVE' ? 'active' : 'cancelled'}.` });
      reload();
    } catch (e) {
      setMessage({ kind: 'error', text: e.message });
    } finally {
      setBusyId(null);
    }
  };

  const confirmDelete = async () => {
    const s = toDelete;
    setBusyId(s.id);
    try {
      await api.subscriptions.remove(s.id);
      setMessage({ kind: 'success', text: `${s.serviceName} was deleted.` });
      if (expandedId === s.id) setExpandedId(null);
      reload();
    } catch (e) {
      setMessage({ kind: 'error', text: e.message });
    } finally {
      setBusyId(null);
      setToDelete(null);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Subscriptions</h1>
          <p className="muted">Select a row to see its price history.</p>
        </div>
        <Link to="/manual-entry" className="btn btn-primary">
          Add subscription
        </Link>
      </div>

      {message && (
        <Banner kind={message.kind} onClose={() => setMessage(null)}>
          {message.text}
        </Banner>
      )}

      {loading && !data && <Loading label="Loading subscriptions…" />}
      {error && <ErrorMessage error={error} onRetry={reload} />}

      {data && subs.length === 0 && (
        <Empty title="No subscriptions yet">
          <p>Add your first one to start tracking its price.</p>
          <Link to="/manual-entry" className="btn btn-primary">Add a subscription</Link>
        </Empty>
      )}

      {data && subs.length > 0 && (
        <section className="panel">
          <form className="filters" role="search" onSubmit={(e) => e.preventDefault()}>
            <div className="field">
              <label htmlFor="q">Search service</label>
              <input id="q" type="search" placeholder="e.g. Netflix" value={q} onChange={(e) => setQ(e.target.value)} />
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
            <div className="field">
              <label htmlFor="status">Status</label>
              <select id="status" value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="all">All</option>
                <option value="ACTIVE">Active</option>
                <option value="CANCELLED">Cancelled</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="sort">Sort by</label>
              <select id="sort" value={sort} onChange={(e) => setSort(e.target.value)}>
                <option value="name">Name (A–Z)</option>
                <option value="price-desc">Price (high to low)</option>
                <option value="price-asc">Price (low to high)</option>
                <option value="checked">Recently checked</option>
              </select>
            </div>
            {filtersActive && (
              <button type="button" className="btn btn-secondary" onClick={clearFilters}>
                Clear filters
              </button>
            )}
          </form>

          <p className="muted result-count" aria-live="polite">
            {filtered.length} of {subs.length} subscription{subs.length === 1 ? '' : 's'}
          </p>

          {filtered.length === 0 ? (
            <p className="empty-inline">No subscriptions match your filters.</p>
          ) : (
            <div className="table-wrap">
              <table className="table table-stack">
                <thead>
                  <tr>
                    <th scope="col">Service</th>
                    <th scope="col">Category</th>
                    <th scope="col" className="num">Current price</th>
                    <th scope="col">Status</th>
                    <th scope="col">Last checked</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((s) => {
                    const open = expandedId === s.id;
                    const stale = s.status === 'ACTIVE' && (daysSince(s.lastCheckedDate) ?? 0) > STALE_DAYS;
                    return (
                      <Fragment key={s.id}>
                        <tr className={`row-click${open ? ' row-open' : ''}`}>
                          <td data-label="Service">
                            <button
                              type="button"
                              className="link-button"
                              aria-expanded={open}
                              aria-controls={`history-${s.id}`}
                              onClick={() => setExpandedId(open ? null : s.id)}
                            >
                              <span className="chevron" aria-hidden="true">{open ? '▾' : '▸'}</span> {s.serviceName}
                            </button>
                          </td>
                          <td data-label="Category">{s.category}</td>
                          <td data-label="Current price" className="num">{formatMoney(s.currentPrice)}</td>
                          <td data-label="Status"><StatusBadge status={s.status} /></td>
                          <td data-label="Last checked">
                            {formatDate(s.lastCheckedDate)}
                            {stale && <span className="badge badge-cancel" title={`No renewal seen in over ${STALE_DAYS} days`}> stale</span>}
                          </td>
                        </tr>
                        {open && (
                          <tr className="detail-row" id={`history-${s.id}`}>
                            <td colSpan={5}>
                              <PriceHistory subscriptionId={s.id} />
                              <div className="row-actions">
                                <button
                                  type="button"
                                  className="btn btn-secondary btn-sm"
                                  disabled={busyId === s.id}
                                  onClick={() => toggleStatus(s)}
                                >
                                  {s.status === 'ACTIVE' ? 'Mark as cancelled' : 'Reactivate'}
                                </button>
                                <button
                                  type="button"
                                  className="btn btn-danger-outline btn-sm"
                                  disabled={busyId === s.id}
                                  onClick={() => setToDelete(s)}
                                >
                                  Delete
                                </button>
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          <Pagination page={currentPage} pageSize={PAGE_SIZE} total={filtered.length} onChange={setPage} />
        </section>
      )}

      {toDelete && (
        <ConfirmDialog
          title={`Delete ${toDelete.serviceName}?`}
          message="This removes the subscription and its price history, and cannot be undone."
          confirmLabel="Delete"
          danger
          busy={busyId === toDelete.id}
          onConfirm={confirmDelete}
          onCancel={() => setToDelete(null)}
        />
      )}
    </div>
  );
}

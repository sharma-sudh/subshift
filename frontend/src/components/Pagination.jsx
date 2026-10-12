/** Previous / Next pager. `page` is 1-based. Renders nothing when everything fits on one page. */
export default function Pagination({ page, pageSize, total, onChange }) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  if (total <= pageSize) return null;
  const from = (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, total);
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="pagination-info">
        {from}–{to} of {total}
      </span>
      <div className="pagination-buttons">
        <button type="button" className="btn btn-secondary" onClick={() => onChange(page - 1)} disabled={page <= 1}>
          Previous
        </button>
        <span className="pagination-page">
          Page {page} of {totalPages}
        </span>
        <button type="button" className="btn btn-secondary" onClick={() => onChange(page + 1)} disabled={page >= totalPages}>
          Next
        </button>
      </div>
    </nav>
  );
}

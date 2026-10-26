import { useMemo, useState } from 'react';
import { api } from '../api';
import ConfirmDialog from '../components/ConfirmDialog';
import Field from '../components/Field';
import Pagination from '../components/Pagination';
import { Banner, ErrorMessage, Loading } from '../components/Status';
import TrainingData from '../components/TrainingData';
import { useAsync } from '../hooks/useAsync';
import { hasErrors } from '../utils/validators';

const PAGE_SIZE = 10;

function CatalogTab() {
  const services = useAsync(() => api.catalog.services(), []);
  const categories = useAsync(() => api.catalog.categories(), []);

  const [message, setMessage] = useState(null);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(1);

  // Add form
  const [form, setForm] = useState({ name: '', categoryId: '' });
  const [errors, setErrors] = useState({});
  const [adding, setAdding] = useState(false);

  // Inline edit + delete
  const [editing, setEditing] = useState(null); // { id, name, categoryId }
  const [editError, setEditError] = useState('');
  const [saving, setSaving] = useState(false);
  const [toDelete, setToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const cats = categories.data ?? [];
  const all = services.data ?? [];

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return all
      .filter((s) => !needle || s.name.toLowerCase().includes(needle) || s.categoryName.toLowerCase().includes(needle))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [all, q]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const countsByCategory = useMemo(() => {
    const m = new Map();
    for (const s of all) m.set(s.categoryId, (m.get(s.categoryId) || 0) + 1);
    return m;
  }, [all]);

  const onAdd = async (e) => {
    e.preventDefault();
    setMessage(null);
    const found = {
      name: !form.name.trim() ? 'Service name is required.' : form.name.trim().length > 150 ? 'Use 150 characters or fewer.' : '',
      categoryId: form.categoryId ? '' : 'Choose a category.',
    };
    setErrors(found);
    if (hasErrors(found)) return;
    setAdding(true);
    try {
      const created = await api.catalog.createService(form.name, form.categoryId);
      setMessage({ kind: 'success', text: `Added ${created.name}.` });
      setForm({ name: '', categoryId: '' });
      services.reload();
    } catch (err) {
      setMessage({ kind: 'error', text: err.message });
    } finally {
      setAdding(false);
    }
  };

  const startEdit = (s) => {
    setEditError('');
    setEditing({ id: s.id, name: s.name, categoryId: String(s.categoryId) });
  };

  const saveEdit = async () => {
    if (!editing.name.trim()) return setEditError('Name is required.');
    if (!editing.categoryId) return setEditError('Choose a category.');
    setSaving(true);
    setEditError('');
    try {
      await api.catalog.updateService(editing.id, editing.name, editing.categoryId);
      setEditing(null);
      setMessage({ kind: 'success', text: 'Service updated.' });
      services.reload();
    } catch (err) {
      setEditError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    setDeleting(true);
    try {
      await api.catalog.deleteService(toDelete.id);
      setMessage({ kind: 'success', text: `Deleted ${toDelete.name}.` });
      services.reload();
    } catch (err) {
      setMessage({ kind: 'error', text: err.message });
    } finally {
      setDeleting(false);
      setToDelete(null);
    }
  };

  return (
    <>
      {message && (
        <Banner kind={message.kind} onClose={() => setMessage(null)}>
          {message.text}
        </Banner>
      )}

      <div className="grid-2 grid-admin">
        <section className="panel" aria-labelledby="add-service">
          <h2 id="add-service">Add a service</h2>
          {categories.loading && !categories.data && <Loading label="Loading categories…" />}
          {categories.error && <ErrorMessage error={categories.error} onRetry={categories.reload} />}
          {categories.data && (
            <form onSubmit={onAdd} noValidate>
              <Field id="svc-name" label="Service name" error={errors.name}>
                {(p) => (
                  <input
                    {...p}
                    type="text"
                    placeholder="e.g. Disney+ Hotstar"
                    value={form.name}
                    onChange={(e) => {
                      setForm((f) => ({ ...f, name: e.target.value }));
                      if (errors.name) setErrors((er) => ({ ...er, name: '' }));
                    }}
                  />
                )}
              </Field>
              <Field id="svc-cat" label="Category" error={errors.categoryId}>
                {(p) => (
                  <select
                    {...p}
                    value={form.categoryId}
                    onChange={(e) => {
                      setForm((f) => ({ ...f, categoryId: e.target.value }));
                      if (errors.categoryId) setErrors((er) => ({ ...er, categoryId: '' }));
                    }}
                  >
                    <option value="">Select a category…</option>
                    {cats.map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                )}
              </Field>
              <button type="submit" className="btn btn-primary" disabled={adding}>
                {adding ? 'Adding…' : 'Add service'}
              </button>
            </form>
          )}

          <h3 className="subhead">Categories</h3>
          <ul className="plain-list">
            {cats.map((c) => (
              <li key={c.id}>
                <span>{c.name}</span>
                <span className="muted">{countsByCategory.get(c.id) || 0} service{(countsByCategory.get(c.id) || 0) === 1 ? '' : 's'}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="panel" aria-labelledby="all-services">
          <h2 id="all-services">All services ({all.length})</h2>
          <div className="field">
            <label htmlFor="svc-search">Search</label>
            <input
              id="svc-search"
              type="search"
              placeholder="Name or category"
              value={q}
              onChange={(e) => {
                setQ(e.target.value);
                setPage(1);
              }}
            />
          </div>

          {services.loading && !services.data && <Loading label="Loading services…" />}
          {services.error && <ErrorMessage error={services.error} onRetry={services.reload} />}

          {services.data && (filtered.length === 0 ? (
            <p className="empty-inline">No services found.</p>
          ) : (
            <div className="table-wrap">
              <table className="table table-stack">
                <thead>
                  <tr>
                    <th scope="col">Service</th>
                    <th scope="col">Category</th>
                    <th scope="col"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((s) =>
                    editing?.id === s.id ? (
                      <tr key={s.id}>
                        <td data-label="Service">
                          <input
                            aria-label="Service name"
                            type="text"
                            value={editing.name}
                            onChange={(e) => setEditing((x) => ({ ...x, name: e.target.value }))}
                          />
                          {editError && <p className="field-error" role="alert">{editError}</p>}
                        </td>
                        <td data-label="Category">
                          <select
                            aria-label="Category"
                            value={editing.categoryId}
                            onChange={(e) => setEditing((x) => ({ ...x, categoryId: e.target.value }))}
                          >
                            {cats.map((c) => (
                              <option key={c.id} value={c.id}>{c.name}</option>
                            ))}
                          </select>
                        </td>
                        <td className="actions-cell">
                          <button type="button" className="btn btn-primary btn-sm" onClick={saveEdit} disabled={saving}>
                            {saving ? 'Saving…' : 'Save'}
                          </button>
                          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing(null)} disabled={saving}>
                            Cancel
                          </button>
                        </td>
                      </tr>
                    ) : (
                      <tr key={s.id}>
                        <td data-label="Service">{s.name}</td>
                        <td data-label="Category">{s.categoryName}</td>
                        <td className="actions-cell">
                          <button type="button" className="btn btn-secondary btn-sm" onClick={() => startEdit(s)}>
                            Edit
                          </button>
                          <button type="button" className="btn btn-danger-outline btn-sm" onClick={() => setToDelete(s)}>
                            Delete
                          </button>
                        </td>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </div>
          ))}
          <Pagination page={currentPage} pageSize={PAGE_SIZE} total={filtered.length} onChange={setPage} />
        </section>
      </div>

      {toDelete && (
        <ConfirmDialog
          title={`Delete ${toDelete.name}?`}
          message="This removes the service from the catalog. It can't be deleted while any user is subscribed to it."
          confirmLabel="Delete"
          danger
          busy={deleting}
          onConfirm={confirmDelete}
          onCancel={() => setToDelete(null)}
        />
      )}
    </>
  );
}

const TABS = [
  { id: 'catalog', label: 'Catalog', title: 'Admin: catalog', blurb: 'Manage the shared list of services users can subscribe to.' },
  { id: 'training', label: 'Training data', title: 'Admin: training data', blurb: 'Labeled emails used to train the renewal classifier.' },
];

export default function Admin() {
  const [tab, setTab] = useState('catalog');
  const current = TABS.find((t) => t.id === tab);
  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{current.title}</h1>
          <p className="muted">{current.blurb}</p>
        </div>
      </div>

      <div className="tabs" role="tablist" aria-label="Admin sections">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            id={`admin-tab-${t.id}`}
            aria-selected={tab === t.id}
            aria-controls="admin-tabpanel"
            className={`tab${tab === t.id ? ' tab-active' : ''}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div role="tabpanel" id="admin-tabpanel" aria-labelledby={`admin-tab-${tab}`}>
        {tab === 'catalog' ? <CatalogTab /> : <TrainingData />}
      </div>
    </div>
  );
}

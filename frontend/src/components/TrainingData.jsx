import { useState } from 'react';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime } from '../utils/format';
import ConfirmDialog from './ConfirmDialog';
import Field from './Field';
import Pagination from './Pagination';
import { Banner, ErrorMessage, Loading } from './Status';

const PAGE_SIZE = 10;
const MAX_TEXT = 20000;
const LABELS = { RENEWAL: 'Renewal', NOT_RENEWAL: 'Not renewal' };

function LabelBadge({ label }) {
  return <span className={`badge ${label === 'RENEWAL' ? 'badge-ok' : 'badge-muted'}`}>{LABELS[label] || label}</span>;
}

/** 0.923 -> "92.3%". Values already above 1 are treated as percentages. */
const pct = (v) => (v === null || v === undefined || Number.isNaN(Number(v)) ? '—' : `${(Number(v) <= 1 ? Number(v) * 100 : Number(v)).toFixed(1)}%`);

/** Retrain button + the cross-validated scores it returns. */
function ClassifierTrainer({ onTrained }) {
  const [training, setTraining] = useState(false);
  const [error, setError] = useState('');
  const [metrics, setMetrics] = useState(null);

  const train = async () => {
    setTraining(true);
    setError('');
    try {
      const m = await api.admin.classifier.train();
      setMetrics(m);
      onTrained?.();
    } catch (e) {
      setError(e.message);
    } finally {
      setTraining(false);
    }
  };

  const models = metrics?.models ?? [];
  const served = models.find((m) => m.name === metrics?.servedModel) ?? models[0];

  return (
    <section className="panel" aria-labelledby="clf">
      <div className="panel-head">
        <h2 id="clf">Email classifier</h2>
        <button type="button" className="btn btn-primary" onClick={train} disabled={training} aria-busy={training}>
          {training ? (
            <>
              <span className="spinner spinner-inline" aria-hidden="true" /> Training…
            </>
          ) : (
            'Retrain classifier'
          )}
        </button>
      </div>
      <p className="muted">
        Retrains on every labeled email below. Scores come from cross-validation, so they reflect emails the model did not
        train on.
      </p>

      {error && <Banner kind="error" onClose={() => setError('')}>{error}</Banner>}

      {metrics && served && (
        <div aria-live="polite">
          <p className="muted small">
            Trained {formatDateTime(metrics.trainedAt)} on {metrics.trainingEmails} emails ({metrics.renewals} renewal,{' '}
            {metrics.nonRenewals} not renewal), {metrics.folds}-fold cross-validation. Served model: <strong>{metrics.servedModel}</strong>.
          </p>
          <dl className="sync-stats">
            <div><dt>Accuracy</dt><dd>{pct(served.accuracy)}</dd></div>
            <div><dt>Precision</dt><dd>{pct(served.precision)}</dd></div>
            <div><dt>Recall</dt><dd>{pct(served.recall)}</dd></div>
            <div><dt>F1</dt><dd>{pct(served.f1)}</dd></div>
          </dl>
          {models.length > 1 && (
            <div className="table-wrap">
              <table className="table table-stack">
                <thead>
                  <tr>
                    <th scope="col">Model</th>
                    <th scope="col" className="num">Accuracy</th>
                    <th scope="col" className="num">Precision</th>
                    <th scope="col" className="num">Recall</th>
                    <th scope="col" className="num">F1</th>
                  </tr>
                </thead>
                <tbody>
                  {models.map((m) => (
                    <tr key={m.name}>
                      <td data-label="Model">
                        {m.name} {m.name === metrics.servedModel && <span className="badge badge-ok">In use</span>}
                      </td>
                      <td data-label="Accuracy" className="num">{pct(m.accuracy)}</td>
                      <td data-label="Precision" className="num">{pct(m.precision)}</td>
                      <td data-label="Recall" className="num">{pct(m.recall)}</td>
                      <td data-label="F1" className="num">{pct(m.f1)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

/** Add / edit form. `initial` = null for add, or { id, emailText, label } for edit. */
function TrainingForm({ initial, onSaved, onCancel }) {
  const editing = Boolean(initial?.id);
  const [emailText, setEmailText] = useState(initial?.emailText ?? '');
  const [label, setLabel] = useState(initial?.label ?? '');
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    const found = {
      emailText: !emailText.trim() ? 'Paste the email text (subject and body).' : emailText.length > MAX_TEXT ? `Use ${MAX_TEXT.toLocaleString()} characters or fewer.` : '',
      label: label ? '' : 'Choose a label.',
    };
    setErrors(found);
    if (found.emailText || found.label) return;
    setSaving(true);
    setServerError('');
    try {
      const body = { emailText, label };
      if (editing) await api.admin.training.update(initial.id, body);
      else await api.admin.training.create(body);
      onSaved(editing ? 'Training email updated.' : 'Training email added.');
    } catch (err) {
      setServerError(err.message);
      setSaving(false);
    }
  };

  return (
    <section className="panel" aria-labelledby="train-form">
      <h2 id="train-form">{editing ? `Edit training email #${initial.id}` : 'Add a training email'}</h2>
      <form onSubmit={submit} noValidate>
        <Field id="tr-text" label="Email text" error={errors.emailText} hint={`${emailText.length.toLocaleString()} / ${MAX_TEXT.toLocaleString()} characters`}>
          {(p) => (
            <textarea
              {...p}
              rows={8}
              value={emailText}
              placeholder={'Subject: Your Netflix membership renewed\n\nHi, we charged ₹649 for your monthly plan…'}
              onChange={(e) => {
                setEmailText(e.target.value);
                if (errors.emailText) setErrors((x) => ({ ...x, emailText: '' }));
              }}
            />
          )}
        </Field>
        <Field id="tr-label" label="Label" error={errors.label}>
          {(p) => (
            <select
              {...p}
              value={label}
              onChange={(e) => {
                setLabel(e.target.value);
                if (errors.label) setErrors((x) => ({ ...x, label: '' }));
              }}
            >
              <option value="">Select a label…</option>
              <option value="RENEWAL">Renewal (a subscription charge or renewal)</option>
              <option value="NOT_RENEWAL">Not renewal (anything else)</option>
            </select>
          )}
        </Field>
        {serverError && <Banner kind="error">{serverError}</Banner>}
        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : editing ? 'Save changes' : 'Add email'}
          </button>
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </button>
        </div>
      </form>
    </section>
  );
}

export default function TrainingData() {
  const [label, setLabel] = useState('');
  const [page, setPage] = useState(1);
  const [message, setMessage] = useState(null);
  const [form, setForm] = useState(null); // null | { mode: 'add' } | { mode: 'edit', initial }
  const [loadingEditId, setLoadingEditId] = useState(null);
  const [toDelete, setToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const stats = useAsync(() => api.admin.training.stats(), []);
  const list = useAsync(() => api.admin.training.list({ label, page, size: PAGE_SIZE }), [label, page]);

  const refresh = () => {
    stats.reload();
    list.reload();
  };

  const items = list.data?.items ?? [];
  const total = list.data?.totalItems ?? 0;

  const startEdit = async (row) => {
    setLoadingEditId(row.id);
    setMessage(null);
    try {
      const full = await api.admin.training.get(row.id); // the list only carries a short preview
      setForm({ mode: 'edit', initial: { id: full.id, emailText: full.emailText, label: full.label } });
    } catch (e) {
      setMessage({ kind: 'error', text: e.message });
    } finally {
      setLoadingEditId(null);
    }
  };

  const onSaved = (text) => {
    setForm(null);
    setMessage({ kind: 'success', text });
    refresh();
  };

  const confirmDelete = async () => {
    setDeleting(true);
    try {
      await api.admin.training.remove(toDelete.id);
      setMessage({ kind: 'success', text: `Deleted training email #${toDelete.id}.` });
      // Deleting the last row on a page would leave an empty page; step back one.
      if (items.length === 1 && page > 1) setPage(page - 1);
      else list.reload();
      stats.reload();
    } catch (e) {
      setMessage({ kind: 'error', text: e.message });
    } finally {
      setDeleting(false);
      setToDelete(null);
    }
  };

  const s = stats.data;

  return (
    <>
      <section aria-label="Training data totals">
        {stats.error && <ErrorMessage error={stats.error} onRetry={stats.reload} />}
        <div className="stats stats-3">
          <div className="stat stat-primary">
            <span className="stat-label">Labeled emails</span>
            <span className="stat-value">{s ? s.total : '…'}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Renewal</span>
            <span className="stat-value">{s ? s.renewal : '…'}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Not renewal</span>
            <span className="stat-value">{s ? s.notRenewal : '…'}</span>
          </div>
        </div>
      </section>

      <ClassifierTrainer />

      {message && (
        <Banner kind={message.kind} onClose={() => setMessage(null)}>
          {message.text}
        </Banner>
      )}

      {form && (
        <TrainingForm
          key={form.initial?.id ?? 'new'}
          initial={form.initial}
          onSaved={onSaved}
          onCancel={() => setForm(null)}
        />
      )}

      <section className="panel" aria-labelledby="train-list">
        <div className="panel-head">
          <h2 id="train-list">Training emails</h2>
          {!form && (
            <button type="button" className="btn btn-primary btn-sm" onClick={() => setForm({ mode: 'add' })}>
              Add email
            </button>
          )}
        </div>

        <div className="field filter-field">
          <label htmlFor="tr-filter">Label</label>
          <select
            id="tr-filter"
            value={label}
            onChange={(e) => {
              setLabel(e.target.value);
              setPage(1);
            }}
          >
            <option value="">All labels</option>
            <option value="RENEWAL">Renewal</option>
            <option value="NOT_RENEWAL">Not renewal</option>
          </select>
        </div>

        {list.loading && !list.data && <Loading label="Loading training emails…" />}
        {list.error && <ErrorMessage error={list.error} onRetry={list.reload} />}

        {list.data && (items.length === 0 ? (
          <p className="empty-inline">{label ? 'No emails with that label.' : 'No training emails yet. Add one to get started.'}</p>
        ) : (
          <div className="table-wrap" aria-busy={list.loading}>
            <table className="table table-stack">
              <thead>
                <tr>
                  <th scope="col">Preview</th>
                  <th scope="col">Label</th>
                  <th scope="col">Labeled by</th>
                  <th scope="col">Added</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {items.map((r) => (
                  <tr key={r.id}>
                    <td data-label="Preview" className="preview-cell">{r.preview}</td>
                    <td data-label="Label"><LabelBadge label={r.label} /></td>
                    <td data-label="Labeled by">{r.labeledBy || '—'}</td>
                    <td data-label="Added">{formatDateTime(r.createdAt)}</td>
                    <td className="actions-cell">
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => startEdit(r)} disabled={loadingEditId === r.id}>
                        {loadingEditId === r.id ? 'Loading…' : 'Edit'}
                      </button>
                      <button type="button" className="btn btn-danger-outline btn-sm" onClick={() => setToDelete(r)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
        <Pagination page={page} pageSize={PAGE_SIZE} total={total} onChange={setPage} />
      </section>

      {toDelete && (
        <ConfirmDialog
          title={`Delete training email #${toDelete.id}?`}
          message="It will no longer be used the next time the classifier is trained."
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

import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import Field from '../components/Field';
import { Banner, ErrorMessage, Loading } from '../components/Status';
import { useAsync } from '../hooks/useAsync';
import { formatDate, formatMoney, todayISO } from '../utils/format';
import { hasErrors, isValidPrice } from '../utils/validators';

const EMPTY = { serviceId: '', price: '', dateRecorded: '' };

export default function ManualEntry() {
  const services = useAsync(() => api.catalog.services(), []);
  const [form, setForm] = useState({ ...EMPTY, dateRecorded: todayISO() });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState('');
  const [result, setResult] = useState(null);

  // Group the dropdown by category so a long catalog stays scannable.
  const grouped = useMemo(() => {
    const groups = new Map();
    for (const s of services.data ?? []) {
      if (!groups.has(s.categoryName)) groups.set(s.categoryName, []);
      groups.get(s.categoryName).push(s);
    }
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([name, list]) => [name, list.sort((a, b) => a.name.localeCompare(b.name))]);
  }, [services.data]);

  const set = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    if (errors[key]) setErrors((er) => ({ ...er, [key]: '' }));
  };

  const validate = () => ({
    serviceId: form.serviceId ? '' : 'Choose a service.',
    price: !String(form.price).trim()
      ? 'Price is required.'
      : !isValidPrice(form.price)
        ? 'Enter a positive amount with at most 2 decimal places.'
        : '',
    dateRecorded: !form.dateRecorded
      ? 'Date is required.'
      : form.dateRecorded > todayISO()
        ? 'The date cannot be in the future.'
        : '',
  });

  const onSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    setResult(null);
    const found = validate();
    setErrors(found);
    if (hasErrors(found)) return;

    setSubmitting(true);
    try {
      const { subscription, created } = await api.subscriptions.manual(form);
      setResult({ subscription, created });
      setForm({ ...EMPTY, dateRecorded: todayISO() });
    } catch (err) {
      setApiError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page narrow">
      <div className="page-head">
        <div>
          <h1>Add a subscription</h1>
          <p className="muted">
            For services that don’t send parseable emails. If the price matches your last recorded one, nothing new is logged.
          </p>
        </div>
      </div>

      {result && (
        <Banner kind="success" onClose={() => setResult(null)}>
          <strong>{result.created ? 'Subscription added: ' : 'Subscription updated: '}</strong>
          {result.subscription.serviceName} is now {formatMoney(result.subscription.currentPrice)}
          {result.subscription.lastCheckedDate && <> (checked {formatDate(result.subscription.lastCheckedDate)})</>}.{' '}
          <Link to="/subscriptions">View subscriptions</Link>
        </Banner>
      )}
      {apiError && <Banner kind="error">{apiError}</Banner>}

      <section className="panel">
        {services.loading && !services.data && <Loading label="Loading services…" />}
        {services.error && <ErrorMessage error={services.error} onRetry={services.reload} />}

        {services.data && (
          <form onSubmit={onSubmit} noValidate>
            <Field id="serviceId" label="Service" error={errors.serviceId}>
              {(p) => (
                <select {...p} value={form.serviceId} onChange={set('serviceId')}>
                  <option value="">Select a service…</option>
                  {grouped.map(([cat, list]) => (
                    <optgroup key={cat} label={cat}>
                      {list.map((s) => (
                        <option key={s.id} value={s.id}>{s.name}</option>
                      ))}
                    </optgroup>
                  ))}
                </select>
              )}
            </Field>

            <Field id="price" label="Price (₹)" error={errors.price} hint="What you were charged for this billing period.">
              {(p) => (
                <input {...p} type="number" inputMode="decimal" min="0.01" step="0.01" placeholder="199" value={form.price} onChange={set('price')} />
              )}
            </Field>

            <Field id="dateRecorded" label="Date charged" error={errors.dateRecorded}>
              {(p) => <input {...p} type="date" max={todayISO()} value={form.dateRecorded} onChange={set('dateRecorded')} />}
            </Field>

            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Saving…' : 'Save subscription'}
            </button>
          </form>
        )}
      </section>
    </div>
  );
}

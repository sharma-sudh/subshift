import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Field from '../components/Field';
import { Banner } from '../components/Status';
import { hasErrors, isEmail } from '../utils/validators';

export default function Register() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (user) return <Navigate to="/dashboard" replace />;

  const set = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    if (errors[key]) setErrors((er) => ({ ...er, [key]: '' }));
  };

  // Mirrors the backend's RegisterRequest rules: name <= 100, email <= 255, password 8..72.
  const validate = () => ({
    name: !form.name.trim() ? 'Name is required.' : form.name.trim().length > 100 ? 'Name must be 100 characters or fewer.' : '',
    email: !form.email.trim() ? 'Email is required.' : !isEmail(form.email) ? 'Enter a valid email address.' : '',
    password: !form.password
      ? 'Password is required.'
      : form.password.length < 8
        ? 'Use at least 8 characters.'
        : form.password.length > 72
          ? 'Use 72 characters or fewer.'
          : '',
    confirm: form.confirm !== form.password ? 'Passwords do not match.' : '',
  });

  const onSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    const found = validate();
    setErrors(found);
    if (hasErrors(found)) return;
    setSubmitting(true);
    try {
      await register(form.name, form.email, form.password);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setApiError(err.message);
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Create your account</h1>
        <p className="muted">Track subscriptions and get alerted when prices creep up.</p>
        {apiError && <Banner kind="error">{apiError}</Banner>}
        <form onSubmit={onSubmit} noValidate>
          <Field id="name" label="Name" error={errors.name}>
            {(p) => <input {...p} type="text" autoComplete="name" value={form.name} onChange={set('name')} autoFocus />}
          </Field>
          <Field id="email" label="Email" error={errors.email}>
            {(p) => <input {...p} type="email" autoComplete="email" value={form.email} onChange={set('email')} />}
          </Field>
          <Field id="password" label="Password" error={errors.password} hint="At least 8 characters.">
            {(p) => <input {...p} type="password" autoComplete="new-password" value={form.password} onChange={set('password')} />}
          </Field>
          <Field id="confirm" label="Confirm password" error={errors.confirm}>
            {(p) => <input {...p} type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} />}
          </Field>
          <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
            {submitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <p className="auth-switch">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
}

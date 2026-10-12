import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Field from '../components/Field';
import { Banner } from '../components/Status';
import { hasErrors, isEmail } from '../utils/validators';

export default function Login() {
  const { user, login, notice, clearNotice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (user) return <Navigate to="/dashboard" replace />;

  const set = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    if (errors[key]) setErrors((er) => ({ ...er, [key]: '' }));
  };

  const validate = () => ({
    email: !form.email.trim() ? 'Email is required.' : !isEmail(form.email) ? 'Enter a valid email address.' : '',
    password: !form.password ? 'Password is required.' : '',
  });

  const onSubmit = async (e) => {
    e.preventDefault();
    clearNotice();
    setApiError('');
    const found = validate();
    setErrors(found);
    if (hasErrors(found)) return;
    setSubmitting(true);
    try {
      await login(form.email, form.password);
      navigate(location.state?.from || '/dashboard', { replace: true });
    } catch (err) {
      setApiError(err.message);
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Sign in to SubShift</h1>
        <p className="muted">Catch the price hikes your subscriptions slipped in.</p>
        {notice && <Banner kind="warn">{notice}</Banner>}
        {apiError && <Banner kind="error">{apiError}</Banner>}
        <form onSubmit={onSubmit} noValidate>
          <Field id="email" label="Email" error={errors.email}>
            {(p) => <input {...p} type="email" autoComplete="email" value={form.email} onChange={set('email')} autoFocus />}
          </Field>
          <Field id="password" label="Password" error={errors.password}>
            {(p) => <input {...p} type="password" autoComplete="current-password" value={form.password} onChange={set('password')} />}
          </Field>
          <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="auth-switch">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </div>
    </div>
  );
}

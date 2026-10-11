import { useAuth } from '../auth/AuthContext';

// Shell only: the real summary and charts are wired in a later commit.
export default function Dashboard() {
  const { user } = useAuth();
  return (
    <div className="page">
      <h1>SubShift Dashboard</h1>
      <p className="muted">Welcome back, {user.name}.</p>
      <section className="panel">
        <h2>Subscriptions</h2>
        <p className="muted">Your subscription list will appear here.</p>
      </section>
    </div>
  );
}

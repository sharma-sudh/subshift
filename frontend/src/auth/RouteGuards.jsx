import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/** Any page behind login. Remembers where the user was heading so login can send them back. */
export function RequireAuth() {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}

/** Admin-only pages. The backend enforces this too (/api/admin/** needs ROLE_ADMIN); this just hides the UI. */
export function RequireAdmin() {
  const { isAdmin } = useAuth();
  if (!isAdmin) {
    return (
      <div className="page narrow">
        <div className="panel empty">
          <h1>Admins only</h1>
          <p>Your account does not have permission to view this page.</p>
        </div>
      </div>
    );
  }
  return <Outlet />;
}

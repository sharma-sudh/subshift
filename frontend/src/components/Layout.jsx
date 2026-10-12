import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth/AuthContext';
import { RoleBadge } from './Badges';

export default function Layout() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [unread, setUnread] = useState(0);

  // Close the mobile menu after navigating.
  useEffect(() => setMenuOpen(false), [location.pathname]);

  // Unread-alert badge in the nav. Best effort: a failure here should never break the page.
  useEffect(() => {
    let cancelled = false;
    api.alerts
      .list()
      .then((list) => !cancelled && setUnread(list.filter((a) => !a.isRead).length))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/dashboard" className="brand">
            <span className="brand-mark" aria-hidden="true">
              <svg viewBox="0 0 32 32" width="26" height="26">
                <rect width="32" height="32" rx="7" fill="#0f5c55" />
                <path d="M6 21l6-6 5 4 9-10" fill="none" stroke="#f6c453" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </span>
            SubShift
          </Link>
          <button
            type="button"
            className="menu-toggle"
            aria-expanded={menuOpen}
            aria-controls="main-nav"
            onClick={() => setMenuOpen((o) => !o)}
          >
            {menuOpen ? 'Close' : 'Menu'}
          </button>
          <nav id="main-nav" className={`nav${menuOpen ? ' nav-open' : ''}`} aria-label="Main">
            <NavLink to="/dashboard">Dashboard</NavLink>
            <NavLink to="/subscriptions">Subscriptions</NavLink>
            <NavLink to="/manual-entry">Add subscription</NavLink>
            <NavLink to="/alerts">
              Alerts
              {unread > 0 && <span className="count-pill" aria-label={`${unread} unread`}>{unread}</span>}
            </NavLink>
            <NavLink to="/insights">Insights</NavLink>
            <NavLink to="/gmail">Gmail</NavLink>
            {isAdmin && <NavLink to="/admin">Admin</NavLink>}
            <div className="nav-user">
              <span className="nav-user-name" title={user.email}>
                {user.name}
              </span>
              <RoleBadge role={user.role} />
              <button type="button" className="btn btn-secondary btn-sm" onClick={handleLogout}>
                Log out
              </button>
            </div>
          </nav>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}

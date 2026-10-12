import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAdmin, RequireAuth } from './auth/RouteGuards';
import Layout from './components/Layout';
import Admin from './pages/Admin';
import Alerts from './pages/Alerts';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import ManualEntry from './pages/ManualEntry';
import NotFound from './pages/NotFound';
import Register from './pages/Register';
import Subscriptions from './pages/Subscriptions';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/subscriptions" element={<Subscriptions />} />
          <Route path="/manual-entry" element={<ManualEntry />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route element={<RequireAdmin />}>
            <Route path="/admin" element={<Admin />} />
          </Route>
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}

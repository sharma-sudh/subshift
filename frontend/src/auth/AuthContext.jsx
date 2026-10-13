import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import { safeStorage, setUnauthorizedHandler, tokenStore } from '../api/client';
import { decodeJwt, isExpired } from './jwt';

const AuthContext = createContext(null);
const NAME_KEY = 'subshift_name';
const MAX_TIMEOUT = 2 ** 31 - 1;

/** Rebuilds the session from localStorage. The JWT is the source of truth for email/role/expiry. */
function readSession() {
  const token = tokenStore.get();
  if (!token) return null;
  const payload = decodeJwt(token);
  if (!payload || isExpired(payload)) {
    tokenStore.clear();
    safeStorage('removeItem', NAME_KEY);
    return null;
  }
  return {
    token,
    email: payload.sub,
    role: payload.role,
    exp: payload.exp,
    name: safeStorage('getItem', NAME_KEY) || payload.sub,
  };
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readSession);
  const [notice, setNotice] = useState('');

  const logout = useCallback((message = '') => {
    tokenStore.clear();
    safeStorage('removeItem', NAME_KEY);
    setSession(null);
    setNotice(typeof message === 'string' ? message : '');
  }, []);

  const startSession = useCallback((resp) => {
    tokenStore.set(resp.token);
    safeStorage('setItem', NAME_KEY, resp.name || '');
    setNotice('');
    setSession(readSession());
  }, []);

  const login = useCallback(async (email, password) => startSession(await api.auth.login(email, password)), [startSession]);
  const register = useCallback(
    async (name, email, password) => startSession(await api.auth.register(name, email, password)),
    [startSession],
  );

  // Any 401 from the API (expired/invalid token) ends the session.
  useEffect(() => {
    setUnauthorizedHandler(() => logout('Your session has expired. Please sign in again.'));
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  // Log out automatically at the moment the token expires.
  useEffect(() => {
    if (!session?.exp) return undefined;
    const ms = Math.max(0, session.exp * 1000 - Date.now());
    const id = setTimeout(() => logout('Your session has expired. Please sign in again.'), Math.min(ms, MAX_TIMEOUT));
    return () => clearTimeout(id);
  }, [session, logout]);

  const value = useMemo(
    () => ({
      user: session ? { name: session.name, email: session.email, role: session.role } : null,
      isAdmin: session?.role === 'ADMIN',
      notice,
      clearNotice: () => setNotice(''),
      login,
      register,
      logout: () => logout(''),
    }),
    [session, notice, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

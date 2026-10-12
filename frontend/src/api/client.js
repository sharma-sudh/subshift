// Thin fetch wrapper: base URL, Bearer token, JSON in/out, and one consistent error type.

const BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');
const TOKEN_KEY = 'subshift_token';

/** localStorage can throw (private mode, blocked storage). Treat that as "nothing stored". */
export function safeStorage(op, ...args) {
  try {
    return window.localStorage[op](...args);
  } catch {
    return null;
  }
}

export const tokenStore = {
  get: () => safeStorage('getItem', TOKEN_KEY),
  set: (t) => safeStorage('setItem', TOKEN_KEY, t),
  clear: () => safeStorage('removeItem', TOKEN_KEY),
};

let onUnauthorized = null;
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

// Spring Boot hides exception messages by default (server.error.include-message=never), so the
// response body usually has no useful text. We therefore map by status code, with per-call overrides.
const DEFAULT_MESSAGES = {
  400: 'The server rejected that request. Please check the values and try again.',
  401: 'Your session has expired. Please sign in again.',
  403: "You don't have permission to do that.",
  404: 'That could not be found.',
  409: 'That conflicts with existing data.',
  502: 'An upstream service (Google) failed. Please try again.',
  503: 'That feature is not configured on the server.',
};

export async function requestWithStatus(path, { method = 'GET', body, messages = {}, auth = true } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = tokenStore.get();
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError('Cannot reach the server. Check that the backend is running and try again.', 0);
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!res.ok) {
    if (res.status === 401 && auth && onUnauthorized) onUnauthorized();
    const serverMsg =
      data && typeof data === 'object' && typeof data.message === 'string' && data.message.trim() ? data.message : null;
    const usableServerMsg = serverMsg && !/^validation failed/i.test(serverMsg) ? serverMsg : null;
    const message =
      messages[res.status] ||
      usableServerMsg ||
      DEFAULT_MESSAGES[res.status] ||
      (res.status >= 500 ? 'Something went wrong on the server. Please try again.' : `Request failed (${res.status}).`);
    throw new ApiError(message, res.status);
  }
  return { data, status: res.status };
}

export async function request(path, options) {
  return (await requestWithStatus(path, options)).data;
}

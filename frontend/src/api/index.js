import { request, requestWithStatus } from './client';
import {
  computeBreakdown,
  normalizeAlert,
  normalizeClusters,
  normalizeNotification,
  normalizeSubscription,
  normalizeSummary,
} from '../utils/normalize';

const list = (arr) => (Array.isArray(arr) ? arr : []);

export const auth = {
  login: (email, password) =>
    request('/api/auth/login', {
      method: 'POST',
      auth: false,
      body: { email: email.trim(), password },
      messages: { 401: 'Invalid email or password.', 400: 'Enter a valid email and password.' },
    }),
  register: (name, email, password) =>
    request('/api/auth/register', {
      method: 'POST',
      auth: false,
      body: { name: name.trim(), email: email.trim(), password },
      messages: {
        409: 'That email is already registered. Try signing in instead.',
        400: 'Check your details: a valid email and a password of 8 to 72 characters.',
      },
    }),
};

export const catalog = {
  services: async () => list(await request('/api/services')),
  categories: async () => list(await request('/api/categories')),
  createService: (name, categoryId) =>
    request('/api/admin/services', {
      method: 'POST',
      body: { name: name.trim(), categoryId: Number(categoryId) },
      messages: { 409: 'A service with that name already exists in this category.', 403: 'Admins only.' },
    }),
  updateService: (id, name, categoryId) =>
    request(`/api/admin/services/${id}`, {
      method: 'PUT',
      body: { name: name.trim(), categoryId: Number(categoryId) },
      messages: { 409: 'A service with that name already exists in this category.', 404: 'That service no longer exists.' },
    }),
  deleteService: (id) =>
    request(`/api/admin/services/${id}`, {
      method: 'DELETE',
      messages: { 409: 'This service is used by subscriptions, so it cannot be deleted.', 404: 'That service no longer exists.' },
    }),
};

export const subscriptions = {
  list: async () => list(await request('/api/subscriptions')).map(normalizeSubscription),
  priceHistory: async (id) =>
    list(await request(`/api/subscriptions/${id}/price-history`)).map((p) => ({
      price: Number(p.price),
      dateRecorded: p.dateRecorded,
    })),
  /** GET /api/subscriptions/{id}/forecast. predictedNextPrice is null when fewer than 2 prices exist (then only `note` is useful). */
  forecast: async (id) => {
    const f = await request(`/api/subscriptions/${id}/forecast`, {
      messages: { 404: 'That subscription no longer exists.' },
    });
    return {
      ...f,
      currentPrice: f.currentPrice === null || f.currentPrice === undefined ? null : Number(f.currentPrice),
      predictedNextPrice: f.predictedNextPrice === null || f.predictedNextPrice === undefined ? null : Number(f.predictedNextPrice),
      expectedIncreasePercent: f.expectedIncreasePercent === null || f.expectedIncreasePercent === undefined ? null : Number(f.expectedIncreasePercent),
    };
  },
  setStatus: async (id, status) =>
    normalizeSubscription(await request(`/api/subscriptions/${id}`, { method: 'PUT', body: { status } })),
  remove: (id) =>
    request(`/api/subscriptions/${id}`, { method: 'DELETE', messages: { 404: 'That subscription no longer exists.' } }),
  /** Teammate 2's endpoint. Returns { subscription, created } (HTTP 201 = newly created subscription). */
  manual: async ({ serviceId, price, dateRecorded }) => {
    const { data, status } = await requestWithStatus('/api/subscriptions/manual', {
      method: 'POST',
      body: { serviceId: Number(serviceId), price: Number(price), dateRecorded },
      messages: {
        404: 'Service not found, or the manual-entry endpoint is not available on this server yet.',
        400: 'Check the values: the price must be positive and the date cannot be in the future.',
      },
    });
    return { subscription: normalizeSubscription(data), created: status === 201 };
  },
};

export const alerts = {
  list: async () => list(await request('/api/alerts')).map(normalizeAlert),
  markRead: async (id) => normalizeAlert(await request(`/api/alerts/${id}/read`, { method: 'PATCH' })),
};

export const notifications = {
  list: async () => list(await request('/api/notifications')).map(normalizeNotification),
  markRead: async (id) => normalizeNotification(await request(`/api/notifications/${id}/read`, { method: 'PATCH' })),
  /** Whether alerts are also emailed to the account address. */
  emailPreference: async () => Boolean((await request('/api/notifications/email-preference'))?.enabled),
  setEmailPreference: async (enabled) =>
    Boolean((await request('/api/notifications/email-preference', { method: 'PUT', body: { enabled } }))?.enabled),
  sendTestEmail: () =>
    request('/api/notifications/test-email', {
      method: 'POST',
      messages: {
        503: 'Email alerts are not set up on the server yet (the SMTP settings are missing).',
        502: 'The mail server did not accept the test message. Check the SMTP settings.',
      },
    }),
};

export const dashboard = {
  /**
   * Prefers GET /api/dashboard/summary (view + stored procedure). If that endpoint isn't there
   * yet (or errors), falls back to computing the same numbers from GET /api/subscriptions so the
   * page is useful during development. `source` tells the UI which path was used.
   */
  summary: async () => {
    try {
      const raw = await request('/api/dashboard/summary');
      const s = normalizeSummary(raw);
      if (!Array.isArray(raw.categoryBreakdown)) {
        s.categoryBreakdown = computeBreakdown(s.subscriptions).map(({ category, amount }) => ({ category, amount }));
      }
      return { ...s, source: 'server' };
    } catch (e) {
      if (e.status === 401) throw e;
      const subs = await subscriptions.list();
      const breakdown = computeBreakdown(subs);
      return {
        totalMonthlySpend: breakdown.reduce((sum, r) => sum + r.amount, 0),
        categoryBreakdown: breakdown.map(({ category, amount }) => ({ category, amount })),
        subscriptions: subs,
        source: 'estimated',
      };
    }
  },
  insights: async () => {
    try {
      const raw = await request('/api/dashboard/insights');
      return { clusters: normalizeClusters(raw), spendTiers: raw.spendTiers ?? null, source: 'server' };
    } catch (e) {
      if (e.status === 401) throw e;
      const subs = await subscriptions.list();
      const clusters = computeBreakdown(subs).map((r) => ({
        category: r.category,
        totalSpend: r.amount,
        subscriptionCount: r.count,
      }));
      return { clusters, spendTiers: null, source: 'estimated' };
    }
  },
};

const ALREADY_CONNECTED = 'Another mailbox is already connected. Disconnect it first.';

/** The connected mailbox, whichever provider it is (Gmail, Outlook or Yahoo). */
export const mail = {
  status: () => request('/api/mail/status'),
  disconnect: () => request('/api/mail/disconnect', { method: 'DELETE' }),
  connectGmail: () =>
    request('/api/gmail/connect', {
      messages: { 503: 'Google sign-in is not configured on the server.', 409: ALREADY_CONNECTED },
    }),
  connectOutlook: () =>
    request('/api/mail/outlook/connect', {
      messages: { 503: 'Outlook sign-in is not configured on the server.', 409: ALREADY_CONNECTED },
    }),
  connectYahoo: (email, appPassword) =>
    request('/api/mail/yahoo/connect', {
      method: 'POST',
      body: { email: email.trim(), appPassword },
      messages: {
        400: 'Yahoo rejected that address or app password. Use a Yahoo address and a generated app password, not your normal password.',
        409: ALREADY_CONNECTED,
        502: 'Could not reach Yahoo Mail. Please try again.',
      },
    }),
};

export const gmail = {
  status: () => request('/api/gmail/status'),
  connect: () => request('/api/gmail/connect', { messages: { 503: 'Google sign-in is not configured on the server.' } }),
  disconnect: () => request('/api/gmail/disconnect', { method: 'DELETE' }),
  /**
   * POST /api/email/sync. Saves prices, alerts and notifications. Slow (one Gemini call per email),
   * so callers must show a spinner. Call again while emailsRemaining > 0.
   */
  sync: (limit = 10) =>
    request(`/api/email/sync?limit=${encodeURIComponent(limit)}`, {
      method: 'POST',
      messages: {
<<<<<<< HEAD
        409: 'No mailbox is connected (or the connection has expired). Connect your email first.',
        503: 'Email sync is not configured on the server (Gemini API key missing).',
        502: 'Your mail provider or Gemini failed while syncing. Please try again.',
=======
        409: 'Gmail is not connected (or the connection has expired). Connect Gmail first.',
        503: 'Email sync is not configured on the server (Gemini API key missing).',
        502: 'Google or Gemini failed while syncing. Please try again.',
>>>>>>> 6b6bd0521eb4946bb2bef89b191da62c145f2505
      },
    }),
  scan: (limit) =>
    request(`/api/email/scan?limit=${encodeURIComponent(limit)}`, {
      method: 'POST',
      messages: {
        409: 'No mailbox is connected (or the connection has expired). Connect your email first.',
        503: 'The email scanner is not configured on the server (Gemini API key missing).',
        502: 'Your mail provider or Gemini failed while scanning. Please try again.',
      },
    }),
};

export const admin = {
  training: {
    /** page is 1-based in the UI; the backend is 0-based. label '' = all. */
    list: ({ label = '', page = 1, size = 10 } = {}) => {
      const qs = new URLSearchParams({ page: String(Math.max(0, page - 1)), size: String(size) });
      if (label) qs.set('label', label);
      return request(`/api/admin/training?${qs}`, { messages: { 403: 'Admins only.' } });
    },
    stats: () => request('/api/admin/training/stats', { messages: { 403: 'Admins only.' } }),
    get: (id) => request(`/api/admin/training/${id}`, { messages: { 404: 'That training email no longer exists.' } }),
    create: ({ emailText, label }) =>
      request('/api/admin/training', {
        method: 'POST',
        body: { emailText, label },
        messages: { 409: 'An identical training email already exists.', 400: 'Enter the email text and choose a label.' },
      }),
    update: (id, { emailText, label }) =>
      request(`/api/admin/training/${id}`, {
        method: 'PUT',
        body: { emailText, label },
        messages: { 404: 'That training email no longer exists.', 409: 'An identical training email already exists.', 400: 'Enter the email text and choose a label.' },
      }),
    remove: (id) =>
      request(`/api/admin/training/${id}`, { method: 'DELETE', messages: { 404: 'That training email no longer exists.' } }),
  },
  classifier: {
    /** Retrains from the current training data; returns cross-validated metrics (can take a few seconds). */
    train: () =>
      request('/api/admin/classifier/train', {
        method: 'POST',
        messages: { 400: 'Not enough labeled emails to train yet (need both RENEWAL and NOT_RENEWAL examples).', 409: 'Not enough labeled emails to train yet.' },
      }),
  },
};

<<<<<<< HEAD
export const api = { auth, catalog, subscriptions, alerts, notifications, dashboard, mail, gmail, admin };
=======
export const api = { auth, catalog, subscriptions, alerts, notifications, dashboard, gmail, admin };
>>>>>>> 6b6bd0521eb4946bb2bef89b191da62c145f2505

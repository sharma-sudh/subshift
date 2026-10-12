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
      return { clusters: normalizeClusters(raw), source: 'server' };
    } catch (e) {
      if (e.status === 401) throw e;
      const subs = await subscriptions.list();
      const clusters = computeBreakdown(subs).map((r) => ({
        category: r.category,
        totalSpend: r.amount,
        subscriptionCount: r.count,
      }));
      return { clusters, source: 'estimated' };
    }
  },
};

export const gmail = {
  status: () => request('/api/gmail/status'),
  connect: () => request('/api/gmail/connect', { messages: { 503: 'Google sign-in is not configured on the server.' } }),
  disconnect: () => request('/api/gmail/disconnect', { method: 'DELETE' }),
  scan: (limit) =>
    request(`/api/email/scan?limit=${encodeURIComponent(limit)}`, {
      method: 'POST',
      messages: {
        409: 'Gmail is not connected (or the connection has expired). Connect Gmail first.',
        503: 'The email scanner is not configured on the server (Gemini API key missing).',
        502: 'Google or Gemini failed while scanning. Please try again.',
      },
    }),
};

export const api = { auth, catalog, subscriptions, alerts, notifications, dashboard, gmail };

# SubShift API Reference

Base URL (development): `http://localhost:8080`. All request and response bodies are JSON.

## Conventions

- **Authentication:** send `Authorization: Bearer <token>`. Get a token from `POST /api/auth/login` or `/api/auth/register`. Tokens last 24 hours; after that the API returns `401` and the user logs in again.
- **Roles:** `USER` (every new account) and `ADMIN`. The role is stored inside the token. After an account is promoted to admin, it must log in again to get a token with the new role.
- **Scoping:** every endpoint outside the admin section only ever returns or changes the caller's own data. Another user's record looks exactly like a missing one (`404`).
- **Status codes:** `400` invalid input, `401` missing or expired token, `403` logged in but not allowed (not an admin), `404` not found, `409` conflict (duplicate, or an action that cannot run now).

## Public (no token)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Create an account. Body `{name, email, password}` (password 8 to 72 characters). New accounts are always `USER`. Returns `{token, name, email, role}`. |
| POST | `/api/auth/login` | Body `{email, password}`. Returns `{token, name, email, role}`. |
| GET | `/api/health` | Liveness check. |
| GET | `/api/gmail/callback` | Google redirects the browser here after the Gmail consent screen. The frontend never calls it directly. |

## Any logged-in user

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/auth/me` | The caller's email and authorities. |
| GET | `/api/services` | All services (`id, name, categoryId, categoryName`). |
| GET | `/api/services/{id}` | One service. |
| GET | `/api/categories` | All categories, alphabetical. |
| GET | `/api/subscriptions` | The caller's subscriptions with category and current price. |
| GET | `/api/subscriptions/{id}` | One subscription. |
| GET | `/api/subscriptions/{id}/price-history` | `[{price, dateRecorded}]`, oldest first. |
| POST | `/api/subscriptions` | Body `{serviceId}`. Subscribe to a service. |
| PUT | `/api/subscriptions/{id}` | Body `{status, lastCheckedDate}` (both optional). Status is `ACTIVE` or `CANCELLED`. |
| DELETE | `/api/subscriptions/{id}` | Remove a subscription and its price history. |
| POST | `/api/subscriptions/manual` | Body `{serviceId, price, dateRecorded}`. Creates the subscription if needed and records the price when it changed. `201` if the subscription was new, otherwise `200`. |
| GET | `/api/alerts` | Price-hike alerts, newest first: `{id, serviceName, oldPrice, newPrice, detectedAt, isRead}`. |
| PATCH | `/api/alerts/{id}/read` | Mark one alert as read. |
| GET | `/api/notifications` | Notifications, newest first: `{id, type, message, sentAt, isRead}`. Type is `PRICE_HIKE` or `POSSIBLE_CANCELLATION`. |
| PATCH | `/api/notifications/{id}/read` | Mark one notification as read. |
| GET | `/api/dashboard/summary` | `{totalMonthlySpend, categoryBreakdown: [{category, amount}], subscriptions: [...]}`. |
| GET | `/api/dashboard/insights` | `{clusters: [{category, totalSpend, subscriptionCount}]}`. |
| GET | `/api/gmail/connect` | Returns `{authUrl}`. The frontend then sends the browser to that URL to start the Gmail consent flow. |
| GET | `/api/gmail/status` | `{connected, connectedAt, lastSyncedAt}`. |
| DELETE | `/api/gmail/disconnect` | Revoke access at Google and forget the stored tokens. |
| POST | `/api/email/scan?limit=10` | Dry run: reads recent emails and shows what would be extracted, with the classifier score. Saves nothing. Limit 1 to 25. |
| POST | `/api/email/sync?limit=10` | Reads new emails, extracts renewals and saves prices, alerts and notifications. Can take a while. Repeat while `emailsRemaining` is above 0. |

## Admin only (`ADMIN` role, everyone else gets `403`)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/admin/services` | Body `{name, categoryId}`. Add a service. |
| PUT | `/api/admin/services/{id}` | Same body. Rename or recategorise. |
| DELETE | `/api/admin/services/{id}` | Delete a service. `409` while subscriptions still use it. |
| GET | `/api/admin/training?label=&page=&size=` | Labeled training emails, newest first, with a short preview. `label` is `RENEWAL` or `NOT_RENEWAL` (optional). |
| GET | `/api/admin/training/stats` | `{total, renewal, notRenewal}`. |
| GET | `/api/admin/training/{id}` | One training email with its full text. |
| POST | `/api/admin/training` | Body `{emailText, label}`. `409` if an identical email already exists. |
| POST | `/api/admin/training/batch` | Body `{items: [{emailText, label}]}`, up to 200. Returns `{added, skippedDuplicates}`. |
| PUT | `/api/admin/training/{id}` | Body `{emailText, label}`. Fix a label or the text. |
| DELETE | `/api/admin/training/{id}` | Remove a training email. |
| POST | `/api/admin/classifier/train` | Retrain the renewal classifier and return its cross-validated metrics. |
| GET | `/api/admin/classifier/metrics` | Current accuracy, precision, recall, F1 and the strongest terms. |
| POST | `/api/admin/classifier/predict` | Body `{text}`. Shows the renewal probability for any text. |

## Behaviour worth knowing

- **Price history only stores changes.** A new row is added to `price_log` only when the price differs from the latest one.
- **Alerts come from the database.** A trigger on `price_log` creates an alert whenever a new price is higher than the previous one. The API never inserts alerts itself.
- **Email sync is a pipeline.** Gmail, then the local classifier (skips obvious non-renewals), then Gemini for extraction, then one database transaction that saves everything.
- **Frontend route guards:** show admin pages only when the role decoded from the token is `ADMIN`. The backend enforces the same rule regardless.

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
| GET | `/api/subscriptions/{id}/forecast` | Price forecast for one of the caller's subscriptions: `{subscriptionId, serviceName, currentPrice, predictedNextPrice, expectedIncreasePercent, expectedMonthsUntilChange, expectedChangeDate, priceChangesObserved, note}`. Needs at least two recorded prices. Otherwise `predictedNextPrice`, `expectedIncreasePercent`, `expectedMonthsUntilChange` and `expectedChangeDate` are `null` and `note` explains why (the same happens if the model is not trained yet). `404` if the subscription is not the caller's. |
| POST | `/api/subscriptions` | Body `{serviceId}`. Subscribe to a service. |
| PUT | `/api/subscriptions/{id}` | Body `{status, lastCheckedDate}` (both optional). Status is `ACTIVE` or `CANCELLED`. |
| DELETE | `/api/subscriptions/{id}` | Remove a subscription and its price history. |
| POST | `/api/subscriptions/manual` | Body `{serviceId, price, dateRecorded}`. Creates the subscription if needed and records the price when it changed. `201` if the subscription was new, otherwise `200`. |
| GET | `/api/alerts` | Price-hike alerts, newest first: `{id, serviceName, oldPrice, newPrice, detectedAt, isRead}`. |
| PATCH | `/api/alerts/{id}/read` | Mark one alert as read. |
| GET | `/api/notifications` | Notifications, newest first: `{id, type, message, sentAt, isRead}`. Type is `PRICE_HIKE`, `PRICE_DROP` or `POSSIBLE_CANCELLATION`. |
| PATCH | `/api/notifications/{id}/read` | Mark one notification as read. |
| GET | `/api/dashboard/summary` | `{totalMonthlySpend, categoryBreakdown: [{category, amount}], subscriptions: [...]}`. |
| GET | `/api/dashboard/insights` | `{clusters: [{category, totalSpend, subscriptionCount}], spendTiers}`. `clusters` is spend per category. `spendTiers` groups the caller's priced subscriptions into 2 to 4 price tiers with k-means: `{available, note, k, silhouette, inertia, quality, candidates: [{k, silhouette}], tiers: [{label, minPrice, maxPrice, subscriptionCount, totalSpend, services}]}`. With fewer than 3 different prices `available` is false and only `note` is set. |
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
| POST | `/api/admin/forecast/train` | Retrain the price forecast model on the current price history (plus the synthetic histories) and return its cross-validated metrics. `409` if there are fewer than 20 price-change examples. |
| GET | `/api/admin/forecast/metrics` | Metrics of the current forecast model: `{trainedAt, realSeries, syntheticSeries, realTransitions, syntheticTransitions, folds, models: [{name, rmseRupees, maeRupees, rmseGrowthPoints, maeMonths}], realRidgeRmse, realNoChangeRmse, note}`. `404` until the model has been trained. |
| POST | `/api/admin/notifications/stale-check?days=60` | Runs the stale-subscription check now (`days` optional, 1 to 3650). Returns `{staleDays, checked, created, skippedDuplicates}`. |

## Behaviour worth knowing

- **Price history only stores changes.** A new row is added to `price_log` only when the price differs from the latest one.
- **Alerts come from the database.** A trigger on `price_log` creates an alert whenever a new price differs from the previous one, whether it went up or down (compare `oldPrice` and `newPrice`). The API never inserts alerts itself. Email syncs also create a `PRICE_HIKE` or `PRICE_DROP` notification, and the sync report has a `dropsNotified` count next to `hikesNotified`.
- **Email sync is a pipeline.** Gmail, then the local classifier (skips obvious non-renewals), then Gemini for extraction, then one database transaction that saves everything.
- **Possible cancellation:** a daily job (09:00 server time) creates a `POSSIBLE_CANCELLATION` notification for every `ACTIVE` subscription whose `lastCheckedDate` is more than 60 days old. The same message is not repeated within 30 days. It never changes the subscription's status. Yearly plans can trigger it by mistake.
- **Price forecast:** the model is trained when the backend starts, mostly on synthetic price histories (`data/generate_price_history.py`) plus the real `price_log` data, and an admin can retrain it with `POST /api/admin/forecast/train`. `expectedChangeDate` is the date of the last price change plus the predicted gap. If that date is already in the past, the change is treated as overdue and the date is set 30 days from today, with a different `note`. It is a rough estimate, not a promise.
- **Spend tiers:** k-means runs on the log of each active subscription's latest price, trying 2, 3 and 4 tiers and keeping the best silhouette score. Prices are treated as monthly, like the rest of the dashboard.
- **What the email sync saves:** only emails Gemini marks as a renewal, whose payment did not fail (`paymentFailed` false) and which repeat on a schedule (`recurring` true). `recurring` is also forced to false in code unless the email text mentions repeating billing (subscription, renew, monthly, per month and similar), because the model sometimes calls a one-off purchase recurring. Other outcomes are `skipped_payment_failed` and `skipped_not_recurring`. `/api/email/scan` shows both flags for each email.
- **Frontend route guards:** show admin pages only when the role decoded from the token is `ADMIN`. The backend enforces the same rule regardless.

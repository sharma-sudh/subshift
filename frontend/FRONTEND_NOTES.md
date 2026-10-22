# SubShift frontend: notes

React 18 + Vite + React Router + Recharts. Plain CSS, no UI library.

## Run it
```
cd frontend
npm install
npm run dev        # http://localhost:5173
```
The backend must be running on :8080. In dev, Vite proxies `/api/*` to it, so **no CORS setup is needed locally**.
Override the target with `VITE_PROXY_TARGET` (see `.env.example`).

## Screens
| Route | Who | What |
|---|---|---|
| `/login`, `/register` | public | JWT login / signup, inline validation |
| `/dashboard` | user | monthly spend, active count, unread alerts, category chart, top subscriptions; shows Gmail OAuth result banners |
| `/subscriptions` | user | search, category/status filter, sort, pagination (10/page), click row = price-history chart, cancel/reactivate, delete |
| `/manual-entry` | user | service + price + date form with validation |
| `/alerts` | user | price-hike alerts + notifications tabs, mark read / mark all |
| `/insights` | user | pie + bar + breakdown + summary sentence + price tiers (k-means `spendTiers`; shows only the server note when `available` is false) |
| `/gmail` | user | connect / disconnect, **Sync now** (`POST /api/email/sync`, spinner, totals added up over "Sync more" runs, shows `stopReason`, refreshes `/api/gmail/status`), scan preview |
| `/subscriptions` price history | user | also shows the price forecast (`GET /api/subscriptions/{id}/forecast`); just the server `note` when `predictedNextPrice` is null |
| `/admin` | ADMIN | tabs: **Catalog** (service CRUD, categories overview) and **Training data** (stats header, label filter + server-side pagination, add / edit / delete, "Retrain classifier" showing accuracy, precision, recall, F1) |

Role comes from the JWT `role` claim. The menu and `/admin` route are hidden for USERs, and the backend enforces it independently.

## API contract used (matches the repo's controllers)
Everything below is live (full contract: `docs/API.md`): `/api/auth/*`, `/api/services`, `/api/categories`, `/api/subscriptions[/{id}[/price-history|/forecast]]`, `POST /api/subscriptions/manual`, `/api/alerts[/{id}/read]`, `/api/notifications[/{id}/read]`, `/api/dashboard/summary`, `/api/dashboard/insights` (`{clusters, spendTiers}`), `/api/gmail/{connect,status,disconnect}`, `/api/email/{scan,sync}`, `/api/admin/services[/{id}]`, `/api/admin/training[/stats|/{id}|/batch]`, `/api/admin/classifier/train`.

Summary and insights keep a browser-side fallback (computed from `/api/subscriptions`, with a blue notice) only for when the endpoint errors. `POST /api/admin/training/batch` has no UI yet.

Field-name tolerance (camelCase and the SQL view's snake_case) lives in `src/utils/normalize.js`.

## Things to tell Sudhanshu
1. **CORS for deployment.** `SecurityConfig` has no CORS config. Fine in dev (proxy), but the deployed Netlify/Vercel frontend calling a Render/Railway backend needs it. Add `.cors(Customizer.withDefaults())` and a `CorsConfigurationSource` bean allowing the frontend origin (`FRONTEND_URL`) with `Authorization` + `Content-Type` headers and methods GET/POST/PUT/PATCH/DELETE. Then set `VITE_API_BASE_URL` on the frontend host.
2. **Error messages.** Spring hides `ResponseStatusException` messages by default, so the UI maps messages by HTTP status. Optional: `server.error.include-message: always`.
3. **Admin-only list.** `/admin` is gated in the UI and has Catalog + Training data tabs. A system-wide-alerts admin screen would still need an endpoint.
4. **Gmail redirect.** Backend redirects to `${FRONTEND_URL}/dashboard?gmail=connected|error&reason=...`; `FRONTEND_URL` must match where the frontend is served.
5. Summary/insights JSON shapes: `{totalMonthlySpend, categoryBreakdown:[{category,amount}], subscriptions:[...]}` and `{clusters:[{category,totalSpend,subscriptionCount}], spendTiers:{...}}`.
6. Classifier training returns scores per model under `models[]` (not top level); the UI shows the `servedModel` row as the headline numbers.

## Suggested commits (one per task, from `feature/react-ui` into `dev` via PR)
1. `chore: initialize React app with routing skeleton` (package.json, vite.config.js, index.html, main.jsx, App.jsx)
2. `feat(auth): add login page with JWT handling` (api/client.js, auth/*, Login, Register)
3. `feat(dashboard): add dashboard page shell with logout` (Layout, Dashboard shell)
4. `feat(subscriptions): add subscription list fetching from backend`
5. `feat(dashboard): wire dashboard to backend summary endpoint with charts`
6. `feat(subscriptions): add search, category filter, and pagination`
7. `feat(subscriptions): add price history line chart`
8. `feat(alerts): add alerts page with mark-as-read functionality`
9. `feat(manual-entry): add manual subscription entry form`
10. `feat(admin): add admin panel for managing services and categories`
11. `fix: improve form validation and error handling across the app`
12. `feat(insights): add spend clustering insights page`
13. `feat(notifications): distinguish notification types in UI`
14. `fix(ui): improve responsive layout for mobile screens`
15. `chore(deploy): add production build config for frontend` (netlify.toml / vercel.json)
16. `feat(gmail): add Sync now with multi-run totals`
17. `feat(subscriptions): show price forecast in price history panel`
18. `feat(insights): show spend price tiers`
19. `feat(admin): add training-data tab with classifier retrain`
20. `docs: update frontend notes for live endpoints`

Since everything is built, commit it in logical slices like the above (`git add` the relevant files each time) so your history is real and incremental.

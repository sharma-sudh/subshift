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
| `/insights` | user | pie + bar + breakdown + summary sentence |
| `/gmail` | user | connect / disconnect, scan preview |
| `/admin` | ADMIN | catalog CRUD (add / edit / delete services, categories overview) |

Role comes from the JWT `role` claim. The menu and `/admin` route are hidden for USERs, and the backend enforces it independently.

## API contract used (matches the repo's controllers)
Live today: `/api/auth/*`, `/api/services`, `/api/categories`, `/api/subscriptions[/{id}[/price-history]]`, `/api/alerts[/{id}/read]`, `/api/gmail/{connect,status,disconnect}`, `/api/email/scan`, `/api/admin/services[/{id}]`.

Not merged yet, coded against the agreed contract and **degrading gracefully until they exist**:
| Endpoint | Owner | Behaviour until it exists |
|---|---|---|
| `POST /api/subscriptions/manual` | Teammate 2 | form shows "not available on this server yet" |
| `GET /api/notifications`, `PATCH .../{id}/read` | Teammate 2 | Notifications tab shows an info message |
| `GET /api/dashboard/summary` | Sudhanshu | totals computed in the browser from `/api/subscriptions` (blue notice shown) |
| `GET /api/dashboard/insights` | Sudhanshu | grouped by category in the browser (notice shown) |

Field-name tolerance (camelCase and the SQL view's snake_case) lives in `src/utils/normalize.js`.

## Things to tell Sudhanshu
1. **CORS for deployment.** `SecurityConfig` has no CORS config. Fine in dev (proxy), but the deployed Netlify/Vercel frontend calling a Render/Railway backend needs it. Add `.cors(Customizer.withDefaults())` and a `CorsConfigurationSource` bean allowing the frontend origin (`FRONTEND_URL`) with `Authorization` + `Content-Type` headers and methods GET/POST/PUT/PATCH/DELETE. Then set `VITE_API_BASE_URL` on the frontend host.
2. **Error messages.** Spring hides `ResponseStatusException` messages by default, so the UI maps messages by HTTP status. Optional: `server.error.include-message: always`.
3. **Admin-only list.** Frontend currently gates only `/admin` (calls `/api/admin/services`). Training-data and system-wide-alerts admin screens need endpoints before I can build them.
4. **Gmail redirect.** Backend redirects to `${FRONTEND_URL}/dashboard?gmail=connected|error&reason=...`; `FRONTEND_URL` must match where the frontend is served.
5. Summary/insights JSON shapes: `{totalMonthlySpend, categoryBreakdown:[{category,amount}], subscriptions:[...]}` and `{clusters:[{category,totalSpend,subscriptionCount}]}`.

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

Since everything is built, commit it in logical slices like the above (`git add` the relevant files each time) so your history is real and incremental.

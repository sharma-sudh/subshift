# Deploying SubShift on Render

The backend and its PostgreSQL database run on Render (`render.yaml` describes both). The React frontend goes on Netlify or Vercel.

## Free tier facts to plan around

- A free web service **spins down after 15 minutes without traffic** and takes about a minute to wake. Open the site a few minutes before a demo.
- A sleeping service does not run scheduled jobs, so the daily stale-subscription check may not fire. For a demo use `POST /api/admin/notifications/stale-check`.
- A free Postgres database **expires 30 days after creation** (14-day grace period, then the data is deleted) and has **no backups**. Create it close to the review date.
- The free web service has 512 MB of memory. If the logs show out-of-memory kills, move the service to the Starter instance.

## 1. Generate the secrets (PowerShell)

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)   # JWT_SECRET
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)   # TOKEN_ENCRYPTION_KEY
```

Keep `TOKEN_ENCRYPTION_KEY` safe. Changing it makes every stored Gmail connection unreadable.

## 2. Create the Blueprint

1. Push `render.yaml` and `backend/` to the branch Render will deploy (normally `main`).
2. Render dashboard: New, Blueprint, pick the repo. Render shows the database and the web service.
3. Fill in the prompted values. `GOOGLE_REDIRECT_URI`, `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` depend on URLs you do not have yet, so enter any placeholder now and fix them in step 5.
4. Apply. The first build takes several minutes. The service will fail its first health checks until the schema exists (step 3); that is expected.

## 3. Load the schema (once, no demo data)

In the dashboard open the database and copy the **External Database URL** parts (host, user, password).

```powershell
$env:PGPASSWORD = "<password>"; $env:PGSSLMODE = "require"
psql -h <external-host> -U subshift -d subshift_db -v ON_ERROR_STOP=1 -f D:\SubShift\db\schema.sql
```

Then redeploy the web service (Manual Deploy, Deploy latest commit) so it starts against the tables.

## 4. Check it

```powershell
Invoke-RestMethod https://<backend-url>/api/health
```

## 5. Point everything at the real URLs

1. In the web service environment set `GOOGLE_REDIRECT_URI=https://<backend-url>/api/gmail/callback`.
2. In Google Cloud Console (APIs and Services, Credentials, your OAuth client) add that same URL under **Authorised redirect URIs**.
3. Deploy the frontend with `VITE_API_BASE_URL=https://<backend-url>` (no trailing slash).
4. Set `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` to the frontend origin (for example `https://subshift.netlify.app`, no trailing slash). Render redeploys when an environment variable changes.

## 6. First admin and Gmail

1. Register an account on the live site.
2. Promote it:
   ```powershell
   psql -h <external-host> -U subshift -d subshift_db -c "UPDATE users SET role_id = (SELECT id FROM roles WHERE name='ADMIN') WHERE email='you@example.com';"
   ```
   Log in again so the token carries the new role.
3. Connect Gmail from the app. While the Google consent screen is in Testing mode the refresh token expires after 7 days, so **reconnect right before the demo and the review**.
4. Train the classifier on the live database if you want it (`POST /api/admin/classifier/train`); it needs rows in `classifier_training_emails`.

## 7. Updating an existing deployment (Outlook, Yahoo, plans and alert emails)

1. Apply the database patch to the live database. It is safe to run twice:
   `psql -h <external-host> -U subshift -d subshift_db -v ON_ERROR_STOP=1 -f db/patches/2026-10-25_review_changes.sql`
   Do this before deploying the new backend, because the backend checks the schema at startup.
2. Add the optional environment variables from `render.yaml` in the Render dashboard.
   - **Outlook:** register an Azure app, add the redirect URI `https://<backend-url>/api/mail/outlook/callback`, set `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET` and `MICROSOFT_REDIRECT_URI`.
   - **Alert emails:** set `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` (and `MAIL_PORT` if not 587).
3. Heads-up on SMTP from Render's free tier: as far as I know, free web services block outbound SMTP on the usual ports (25, 465, 587). Check Render's current docs. If your messages time out, try a provider that accepts port 2525, upgrade the plan, or demo alert emails from a local run (`MAIL_*` in `backend/.env`).
4. Services created before this change can include plan variants as separate rows (for example "Claude" and "Claude Pro"). The patch file ends with optional SQL to merge them by hand. New syncs no longer create such duplicates.
5. Apply the stored-procedure patch the same way, before deploying the backend version that calls it:
   `psql -h <external-host> -U subshift -d subshift_db -v ON_ERROR_STOP=1 -f db/patches/2026-10-30_stored_procedure.sql`

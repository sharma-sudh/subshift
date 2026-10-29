# SubShift

A subscription price-drift tracker. SubShift helps users keep track of their recurring subscriptions, detects when a service quietly raises its price, and alerts them so they can decide whether to keep or cancel.

## Live Demo

- Frontend: https://subshift-nine.vercel.app
- Backend API: https://subshift-backend.onrender.com (health check: `/api/health`)

The backend runs on Render's free tier, so it sleeps after 15 minutes without traffic. The first request can take about a minute while it wakes up.

## Project Description

Many people pay for several subscriptions (streaming, productivity tools, fitness apps, utilities) and rarely notice when the price goes up. SubShift solves this by recording the price of each subscription over time and flagging any increase.

**Key features** 

- **User accounts and roles:** secure registration and login with JWT authentication and role-based access (`ADMIN` and `USER`).
- **Subscription tracking:** add subscriptions manually or detect them automatically from renewal emails.
- **Gmail integration:** connect a Gmail account through OAuth, fetch renewal emails, and extract the service name, price and date using Gemini.
- **Email classifier:** a machine-learning model that tells renewal emails apart from other mail.
- **Price history and alerts:** every price change is logged. An increase or a drop automatically creates an alert, and email syncs also create a notification.
- **Possible-cancellation detection:** subscriptions with no recent activity are flagged as possibly cancelled.
- **Price forecast:** predicts a subscription's next price change and roughly when it will happen, from its recorded price history.
- **Dashboard and insights:** monthly spend summary, category breakdown, price history charts, and spend clustering.
- **Admin panel:** admins manage the catalog of services and categories.
- **Multiple mailboxes:** connect Gmail, Outlook (OAuth) or Yahoo Mail (app password).
- **Alert emails:** price changes and possible cancellations can also be emailed over SMTP.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5, Java 17, Maven |
| Persistence | Spring Data JPA (Hibernate) |
| Database | PostgreSQL 15 |
| Security | Spring Security, JWT, BCrypt |
| Frontend | React (Vite), React Router, Recharts |
| External APIs | Google OAuth 2.0 and Gmail API, Gemini API |
| Machine Learning | Written in plain Java with no external ML library: email classifier (TF-IDF features with logistic regression and Naive Bayes), price forecast (ridge regression) and spend clustering (k-means) |
| Containers | Docker (multi-stage backend image) and Docker Compose |
| Testing and CI | JUnit 5 unit tests, SQL tests for the trigger and stored procedure, GitHub Actions |
| Deployment | Render (backend and PostgreSQL database), Vercel (frontend) |

## Prerequisites

Install these before you begin:

- Java 17 (JDK) or newer
- Node.js 18 or newer, with npm
- PostgreSQL 15
- Git
- Maven 3.6.3 or newer (required, there is no Maven wrapper)

The `psql`, `pg_dump` and `pg_restore` commands used below must work in your terminal. On Windows, add the `bin` folder of your PostgreSQL install (for example `C:\Program Files\PostgreSQL\15\bin`) to your `PATH` if they are not found.

## Installation Steps

### 1. Clone the repository

```bash
git clone https://github.com/sharma-sudh/subshift.git
cd subshift
```

### 2. Set up the database

Create the database and load the schema. Run these from the repository root. Each command asks for your Postgres password:

```bash
psql -U postgres -c "CREATE DATABASE subshift_db;"
psql -U postgres -d subshift_db -f db/schema.sql
```

Run `schema.sql` once on an empty database. To start over, run `DROP DATABASE subshift_db;` and repeat both commands.

### 3. Configure environment variables

Copy the example file and fill in your own values. The `.env` file must live in `backend/`, because that is where the app is started. **Never commit the `.env` file.**

macOS, Linux or Git Bash:

```bash
cp backend/.env.example backend/.env
```

PowerShell:

```powershell
Copy-Item backend\.env.example backend\.env
```

The following variables are used:

| Variable | Description |
|---|---|
| `DB_NAME` | Database name (`subshift_db`). The host and port are fixed at `localhost:5432` |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Secret for signing JWTs, at least 32 characters |
| `TOKEN_ENCRYPTION_KEY` | Base64 of exactly 32 random bytes, used to encrypt stored Gmail tokens |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth credentials (optional, only for Gmail) |
| `GEMINI_API_KEY` | Gemini API key (optional, only for email extraction) |
| `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET` | Azure app registration for Outlook sign-in (optional, only for Outlook) |
| `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP settings for alert emails (optional). Without them alerts stay in the app only |
| `GOOGLE_REDIRECT_URI`, `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `GEMINI_MODELS` | Optional overrides, defaults are in `.env.example` |

Generate the two secrets.

macOS, Linux or Git Bash:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # TOKEN_ENCRYPTION_KEY
```

PowerShell:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)   # JWT_SECRET
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)   # TOKEN_ENCRYPTION_KEY
```

Every line in `.env` must be `NAME=value`. `GOOGLE_*`, `MICROSOFT_*`, `MAIL_*` and `GEMINI_API_KEY` can stay empty. Without them the app runs, but the matching feature (Gmail or Outlook connection, alert emails, email extraction) is disabled.

#### Google and Gemini setup (only for the Gmail features)

1. In Google Cloud Console, create a project and enable the Gmail API.
2. Under the OAuth consent screen, choose **External**, keep the status as **Testing**, and add your Google account as a test user.
3. Under Credentials, create an OAuth client ID of type **Web application** with the authorized redirect URI `http://localhost:8080/api/gmail/callback`.
4. Copy the client ID and secret into `backend/.env`.
5. Create a Gemini API key at https://aistudio.google.com/app/apikey and set `GEMINI_API_KEY`.

#### Outlook, Yahoo and alert emails (all optional)

- **Outlook:** in the Azure portal, register an app (App registrations), choose accounts in any organizational directory and personal Microsoft accounts, add the redirect URI `http://localhost:8080/api/mail/outlook/callback` (type Web), add the delegated permissions `Mail.Read` and `offline_access`, create a client secret, and put the IDs in `MICROSOFT_CLIENT_ID` and `MICROSOFT_CLIENT_SECRET`.
- **Yahoo Mail:** needs nothing on the server. Each user creates an app password under Yahoo Account Security (Generate app password) and pastes it on the Email page.
- **Alert emails (SMTP):** set `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_USERNAME` and `MAIL_PASSWORD`. For Gmail use `smtp.gmail.com`, port 587, and a Google app password. Then use **Send test email** on the Alerts page to check it.

### 4. Build the backend

Run this from the repository root. It downloads the dependencies and checks that the code compiles:

```bash
cd backend
mvn -DskipTests package
cd ..
```

### 5. Install frontend dependencies

From the repository root:

```bash
cd frontend
npm install
cd ..
```

## Execution Steps

The backend and the frontend each need their own terminal. Open two terminals and start both in the repository root. PostgreSQL must be running.

### 1. Start the backend (terminal 1)

```bash
cd backend
mvn spring-boot:run
```

The API runs at `http://localhost:8080` by default. Check `http://localhost:8080/api/health`, which should return `SubShift backend is running`. The app won't start if `DB_PASSWORD`, `JWT_SECRET` or `TOKEN_ENCRYPTION_KEY` is missing or invalid.

### 2. Start the frontend (terminal 2)

```bash
cd frontend
npm run dev
```

The app runs at `http://localhost:5173`. In development Vite forwards `/api` to the backend, so no CORS setup is needed.

### 3. Log in

1. Open `http://localhost:5173/register` and create an account. New accounts always get the `USER` role.
2. To make an admin, run this, then sign out and back in, because the role is stored in your login token:

   ```bash
   psql -U postgres -d subshift_db -c "UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN') WHERE email = 'you@example.com';"
   ```

3. The `services` table starts empty. Add services in the Admin page, or load a starter set:

   ```bash
   psql -U postgres -d subshift_db -c "INSERT INTO services (name, category_id) VALUES ('Netflix', (SELECT id FROM categories WHERE name='Entertainment')), ('Spotify', (SELECT id FROM categories WHERE name='Entertainment')), ('Google One', (SELECT id FROM categories WHERE name='Utilities')), ('Microsoft 365', (SELECT id FROM categories WHERE name='Productivity')), ('Cult.fit', (SELECT id FROM categories WHERE name='Fitness'));"
   ```

### 4. Optional: load demo data

For a quick look around without entering data by hand, load the demo accounts and sample subscriptions. Run it once, on a fresh database, after `schema.sql`. It is safe to run after the starter services above. For development only, never on a real deployment:

```bash
psql -U postgres -d subshift_db -f db/demo_seed.sql
```

Then log in as `demo@subshift.test` (user) or `admin@subshift.test` (admin) with the password `Demo@12345`.

### Tests and build check

There are two kinds of automated tests.

**Java unit tests** (JUnit 5, no database needed). They cover JWT creation and validation, the email text preprocessing, the TF-IDF classifiers, ridge regression, k-means clustering and service-name matching. Run them from `backend`:

```bash
cd backend
mvn test
```

**Database tests** for the price-change trigger, the spend-summary function and the `mark_all_alerts_read` stored procedure. They run in one transaction that is rolled back, so they leave no data behind. Run them against a database that has `schema.sql` loaded:

```bash
psql -U postgres -d subshift_db -v ON_ERROR_STOP=1 -f db/tests/trigger_and_procedure_test.sql
```

It prints `All database tests passed` on success. API request examples are in `docs/MANUAL_ENTRY_API_TESTS.md`. To check that everything builds, run `mvn -DskipTests package` in `backend` and `npm run build` in `frontend`. GitHub Actions (`.github/workflows/ci.yml`) runs the schema, the database tests, the backend build with unit tests, a smoke test, the frontend build and a Docker image build on pushes and pull requests to `main` and `dev`.

## Run with Docker

This starts the backend and a PostgreSQL 15 database in containers. The schema is loaded automatically the first time the database starts. Docker Desktop (or Docker Engine with the Compose plugin) is the only thing you need to install.

```bash
cp docker.env.example .env      # PowerShell: Copy-Item docker.env.example .env
```

Open `.env` and set `DB_PASSWORD`, `JWT_SECRET` (at least 32 characters) and `TOKEN_ENCRYPTION_KEY` (base64 of 32 random bytes). The commands to generate the two secrets are in the Installation section. Then:

```bash
docker compose up --build
```

Check `http://localhost:8080/api/health`. Start the frontend as in the Execution steps (`npm run dev` in `frontend`); it talks to the backend on port 8080. To stop, press Ctrl+C or run `docker compose down`. To also delete the database volume and start over, run `docker compose down -v`.

The backend image is published on Docker Hub: https://hub.docker.com/r/sudh14/subshift-backend. To pull it instead of building it:

```bash
docker pull sudh14/subshift-backend:latest
```

## Database Backup and Restore

These commands assume the database is named `subshift_db`. Run them from any folder. Each command asks for your Postgres password. Never commit backup files, because they contain user data.

### Back up

```bash
pg_dump -U postgres -Fc -f subshift_backup.dump subshift_db
```

`-Fc` writes a compressed custom-format archive that `pg_restore` can read.

### Restore into a new database

Restore into a fresh, empty database, never over your working one:

```bash
psql -U postgres -c "CREATE DATABASE subshift_restore_sql;"
pg_restore -U postgres --no-owner -d subshift_restore_sql subshift_backup.dump
```

### Check the restore

Run the same query on both databases. The counts must match:

```bash
psql -U postgres -d subshift_db -c "SELECT (SELECT count(*) FROM users) users, (SELECT count(*) FROM services) services, (SELECT count(*) FROM subscriptions) subscriptions, (SELECT count(*) FROM price_log) price_log, (SELECT count(*) FROM alerts) alerts, (SELECT count(*) FROM notifications) notifications;"
psql -U postgres -d subshift_restore_sql -c "SELECT (SELECT count(*) FROM users) users, (SELECT count(*) FROM services) services, (SELECT count(*) FROM subscriptions) subscriptions, (SELECT count(*) FROM price_log) price_log, (SELECT count(*) FROM alerts) alerts, (SELECT count(*) FROM notifications) notifications;"
```

When you are done, remove the test database:

```bash
psql -U postgres -c "DROP DATABASE subshift_restore_sql;"
```

### Plain SQL alternative

```bash
pg_dump -U postgres -f backup.sql subshift_db
psql -U postgres -c "CREATE DATABASE subshift_restore_sql;"
psql -U postgres -d subshift_restore_sql -f backup.sql
```

## Project Structure

```
.
├── backend/          Spring Boot API (Java 17, Maven)
│   ├── pom.xml
│   ├── .env.example
│   └── src/main/
│       ├── java/com/subshift/backend/
│       │   ├── alerts/  auth/  catalog/  common/  config/
│       │   ├── dashboard/  email/  gmail/  ml/  notifications/
│       │   └── pricelog/  subscriptions/  training/  users/
│       └── resources/application.yml
├── frontend/         React + Vite app
│   └── src/  api/  auth/  components/  hooks/  pages/  utils/
├── db/               schema.sql (tables, view, function, stored procedure, trigger), patches/, tests/, demo_seed.sql
├── data/             Classifier training data and price-history sources
├── docs/             API reference, data dictionary, ER diagram, relational schema, API tests, deployment and security notes, proposal
├── docker-compose.yml  Backend and PostgreSQL in containers
├── render.yaml       Render deployment blueprint
└── .github/workflows/  CI workflow
```

## Team

- Sudhanshu Sharma: schema design, backend core, ML components, RBAC, security
- Ayush Garg: React frontend
- Saksham Goyal: manual-entry endpoint, classifier training-data labeling, documentation

## License

Private academic project for the DBMS course (BCSE307L). Not licensed for reuse.
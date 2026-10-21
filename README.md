# SubShift

A subscription price-drift tracker. SubShift helps users keep track of their recurring subscriptions, detects when a service quietly raises its price, and alerts them so they can decide whether to keep or cancel.

## Project Description

Many people pay for several subscriptions (streaming, productivity tools, fitness apps, utilities) and rarely notice when the price goes up. SubShift solves this by recording the price of each subscription over time and flagging any increase.

**Key features** 

- **User accounts and roles:** secure registration and login with JWT authentication and role-based access (`ADMIN` and `USER`).
- **Subscription tracking:** add subscriptions manually or detect them automatically from renewal emails.
- **Gmail integration:** connect a Gmail account through OAuth, fetch renewal emails, and extract the service name, price and date using Gemini.
- **Email classifier:** a machine-learning model that tells renewal emails apart from other mail.
- **Price history and alerts:** every price change is logged, and a price increase automatically creates an alert and a notification.
- **Possible-cancellation detection:** subscriptions with no recent activity are flagged as possibly cancelled.
- **Dashboard and insights:** monthly spend summary, category breakdown, price history charts, and spend clustering.
- **Admin panel:** admins manage the catalog of services and categories.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5, Java 17, Maven |
| Persistence | Spring Data JPA (Hibernate) |
| Database | PostgreSQL 15 |
| Security | Spring Security, JWT, BCrypt |
| Frontend | React (Vite), React Router, Recharts |
| External APIs | Google OAuth 2.0 and Gmail API, Gemini API |
| Machine Learning | Email classifier written in plain Java (TF-IDF features with logistic regression and Naive Bayes), no external ML library |
| Deployment | <TODO: backend and frontend hosting platforms> |

## Prerequisites

Install these before you begin:

- Java 17 (JDK) or newer
- Node.js 18 or newer, with npm
- PostgreSQL 15
- Git
- Maven 3.6.3 or newer (required, there is no Maven wrapper)

## Installation Steps

### 1. Clone the repository

```bash
git clone https://github.com/sharma-sudh/<repo-name>.git
cd <repo-name>
git checkout dev
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
| `GOOGLE_REDIRECT_URI`, `FRONTEND_URL`, `GEMINI_MODELS` | Optional overrides, defaults are in `.env.example` |

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

Every line in `.env` must be `NAME=value`. `GOOGLE_*` and `GEMINI_API_KEY` can stay empty. Without them the app runs, but Gmail connection and email extraction are disabled.

#### Google and Gemini setup (only for the Gmail features)

1. In Google Cloud Console, create a project and enable the Gmail API.
2. Under the OAuth consent screen, choose **External**, keep the status as **Testing**, and add your Google account as a test user.
3. Under Credentials, create an OAuth client ID of type **Web application** with the authorized redirect URI `http://localhost:8080/api/gmail/callback`.
4. Copy the client ID and secret into `backend/.env`.
5. Create a Gemini API key at https://aistudio.google.com/app/apikey and set `GEMINI_API_KEY`.

### 4. Build the backend

```bash
cd backend
mvn -DskipTests package
```

### 5. Install frontend dependencies

```bash
cd ../frontend
npm install
```

## Execution Steps

### 1. Start the backend

```bash
cd backend
mvn spring-boot:run
```

The API runs at `http://localhost:8080` by default. Check `http://localhost:8080/api/health`, which should return `SubShift backend is running`. The app won't start if `DB_PASSWORD`, `JWT_SECRET` or `TOKEN_ENCRYPTION_KEY` is missing or invalid.

### 2. Start the frontend

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

### Plain SQL alternative

There are no automated tests yet. API test examples are in `docs/MANUAL_ENTRY_API_TESTS.md`. To check that everything builds, run `mvn -DskipTests package` in `backend` and `npm run build` in `frontend`.

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
│       │   ├── email/  gmail/  notifications/  pricelog/
│       │   └── subscriptions/  training/  users/
│       └── resources/application.yml
├── frontend/         React + Vite app
│   └── src/  api/  auth/  components/  hooks/  pages/  utils/
├── db/schema.sql     Tables, view, function and trigger
├── data/             Classifier training data
└── docs/             Data dictionary, API tests, ER diagram, proposal
```

## Team

- Sudhanshu: backend core, ML, security
- Teammate 1: React frontend
- Teammate 2: backend support, data labeling, docs

## License

Private academic project for the DBMS course (BCSE307L). Not licensed for reuse.

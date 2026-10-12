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
| Machine Learning | <TODO: libraries/tools used for the classifier, price prediction and clustering> |
| Deployment | <TODO: backend and frontend hosting platforms> |

## Prerequisites

Install these before you begin:

- Java 17 (JDK)
- Node.js 18 or newer, with npm
- PostgreSQL 15
- Git
- Maven (optional, the project's Maven wrapper can be used instead)

## Installation Steps

### 1. Clone the repository

```bash
<TODO: git clone command>
cd <TODO: repository folder name>
git checkout dev
```

### 2. Set up the database

Create the database and load the schema:

```bash
<TODO: command to create the database, e.g. subshift_db>
<TODO: command to run schema.sql against the database>
```

### 3. Configure environment variables

Copy the example file and fill in your own values. **Never commit the `.env` file.**

```bash
<TODO: command to copy .env.example to .env>
```

The following variables are required:

| Variable | Description |
|---|---|
| <TODO: DB URL variable> | PostgreSQL connection URL |
| <TODO: DB username variable> | Database username |
| <TODO: DB password variable> | Database password |
| <TODO: JWT secret variable> | Secret used to sign JWT tokens |
| <TODO: Google client ID variable> | Google OAuth client ID |
| <TODO: Google client secret variable> | Google OAuth client secret |
| <TODO: Gemini API key variable> | API key for Gemini |
| <TODO: token encryption key variable> | Key used to encrypt stored Gmail tokens |

### 4. Install backend dependencies

```bash
<TODO: backend dependency install command>
```

### 5. Install frontend dependencies

```bash
<TODO: cd into frontend folder>
<TODO: npm install command>
```

## Execution Steps

### 1. Start the backend

```bash
<TODO: command to start the Spring Boot application>
```

The API runs at `http://localhost:8080` by default.

### 2. Start the frontend

```bash
<TODO: command to start the React development server>
```

The app runs at `<TODO: frontend URL and port>`.

### 3. Log in

<TODO: how to create or use a first user and an admin account>

### 4. Run the tests

```bash
<TODO: backend test command>
<TODO: frontend test command, if any>
```

## Project Structure

```
<TODO: paste the final folder structure of the repository>
```

## Team

- <TODO: team member names and roles>

## License

<TODO: add a license, or state that this is a private academic project>

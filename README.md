# SubShift — Subscription Price-Drift Tracker

A DBMS course project (BCSE307L) that detects a user's recurring subscriptions by parsing renewal/payment confirmation emails, logs price history over time, and flags renewals where the price silently increased.

## Project Description
Most people don't notice when a subscription (OTT, music, cloud storage, gym, SaaS) quietly raises its price on renewal. SubShift connects to a user's Gmail (read-only), detects subscription-related emails via a trained classifier, extracts service/price/date via Gemini Flash, and builds a price history per subscription — flagging any renewal that's priced higher than the last recorded payment. Manual entry is available as a fallback. On top of the core tracking, SubShift includes price-prediction and spend-clustering ML components.

## Tech Stack
| Layer | Choice |
|---|---|
| Backend | Spring Boot |
| Database | PostgreSQL |
| ORM | Hibernate / Spring Data JPA |
| Frontend | React (Vite) |
| Auth | JWT + Google OAuth |
| Email access | Gmail API (`gmail.readonly`) |
| Extraction | Gemini Flash |
| ML | Classical ML — logistic regression / Naive Bayes (email classifier), linear regression (price prediction), K-means (spend clustering) |

## Repository Structure
```
subshift/
├── backend/        # Spring Boot project
├── frontend/        # React (Vite) project
├── db/
│   └── schema.sql   # Full PostgreSQL DDL — run this first
├── docs/
│   ├── SubShift_ER_Diagram.png            # Conceptual ER diagram
│   └── SubShift_Relational_Schema.md      # Logical schema, derived from the ER diagram
└── README.md
```

## Installation Steps
**Prerequisites:** Java 17+, Node 18+, PostgreSQL 15+

1. Clone the repo:
   ```
   git clone <repo-url>
   cd subshift
   ```
2. Create the database and run the schema:
   ```
   createdb subshift_db
   psql -d subshift_db -f db/schema.sql
   ```
3. Copy `backend/.env.example` to `backend/.env` and fill in your own values (DB credentials, JWT secret, Google OAuth client ID/secret, Gemini API key) — **never commit this file**.
4. Backend setup:
   ```
   cd backend
   ./mvnw install
   ```
5. Frontend setup:
   ```
   cd frontend
   npm install
   ```

## Execution Steps
1. Start the backend:
   ```
   cd backend
   ./mvnw spring-boot:run
   ```
   Runs on `http://localhost:8080`
2. Start the frontend (separate terminal):
   ```
   cd frontend
   npm run dev
   ```
   Runs on `http://localhost:5173`

## Team
- **Sudhanshu Sharma** — Schema design, backend, ML components, RBAC, security
- **Saksham Goyal** — React frontend
- **Ayush Garg** — Manual-entry backend endpoint, classifier training data, documentation

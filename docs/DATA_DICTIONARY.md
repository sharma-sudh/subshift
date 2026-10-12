# SubShift Data Dictionary

This document describes every table and column in the SubShift PostgreSQL database.

**Key:** PK = Primary Key, FK = Foreign Key, UQ = Unique.

> Data types are PostgreSQL types inferred from the schema design. Verify them against `schema.sql` before finalizing.

---

## 1. roles

Defines the access roles a user can have.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the role. |
| name | VARCHAR(50) | UQ | Role name: `ADMIN` or `USER`. |

---

## 2. users

Stores registered application users and their login credentials.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the user. |
| name | VARCHAR(100) | | Full name of the user. |
| email | VARCHAR(255) | UQ | Email address, used as the login identifier. |
| password_hash | VARCHAR(255) | | BCrypt hash of the user's password (never plain text). |
| role_id | BIGINT | FK → roles.id | Role assigned to the user. |
| created_at | TIMESTAMP | | When the account was created. |

---

## 3. categories

Lookup table of subscription categories.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the category. |
| name | VARCHAR(100) | UQ | Category name: entertainment, productivity, fitness, utilities, or other. |

---

## 4. services

Catalog of subscription services (e.g. Netflix, Spotify) that users can track.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the service. |
| name | VARCHAR(150) | UQ | Name of the service. |
| category_id | BIGINT | FK → categories.id | Category the service belongs to. |
| created_at | TIMESTAMP | | When the service was added to the catalog. |

---

## 5. gmail_connections

Stores a user's linked Gmail account and its encrypted OAuth tokens.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the connection. |
| user_id | BIGINT | FK → users.id | User who connected the Gmail account. |
| access_token_encrypted | TEXT | | Encrypted OAuth access token. |
| refresh_token_encrypted | TEXT | | Encrypted OAuth refresh token used to renew access. |
| connected_at | TIMESTAMP | | When the Gmail account was connected. |
| last_synced_at | TIMESTAMP | | When emails were last fetched from this account. |

---

## 6. subscriptions

Links a user to a service they are subscribed to.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the subscription. |
| user_id | BIGINT | FK → users.id | User who owns the subscription. |
| service_id | BIGINT | FK → services.id | Service being subscribed to. |
| status | VARCHAR(20) | | Subscription state: `ACTIVE` or `CANCELLED`. |
| last_checked_date | DATE | | Last date a price or renewal was recorded for this subscription. |
| created_at | TIMESTAMP | | When the subscription record was created. |

---

## 7. price_log

History of price changes for each subscription. A row is added only when the price changes.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the price entry. |
| subscription_id | BIGINT | FK → subscriptions.id | Subscription this price belongs to. |
| price | NUMERIC(10,2) | | Price charged for the subscription. |
| date_recorded | DATE | | Date the price was observed. |
| source | VARCHAR(20) | | How the price was captured: `EMAIL_PARSED` or `MANUAL`. |

---

## 8. alerts

Price-increase alerts created automatically when a new price is higher than the previous one.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the alert. |
| subscription_id | BIGINT | FK → subscriptions.id | Subscription the alert is about. |
| price_log_id | BIGINT | FK → price_log.id | Price log entry that triggered the alert. |
| old_price | NUMERIC(10,2) | | Price before the increase. |
| new_price | NUMERIC(10,2) | | Price after the increase. |
| detected_at | TIMESTAMP | | When the price increase was detected. |
| is_read | BOOLEAN | | Whether the user has read the alert (default `false`). |

---

## 9. classifier_training_emails

Labeled sample emails used to train the renewal-email classifier.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the training sample. |
| email_text | TEXT | | Full subject and body text of the email. |
| label | VARCHAR(20) | | Classification label: `RENEWAL` or `NOT_RENEWAL`. |
| labeled_by | BIGINT | FK → users.id | User who assigned the label. |
| created_at | TIMESTAMP | | When the sample was added. |

---

## 10. notifications

Messages sent to users about price hikes and possible cancellations.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | BIGINT (auto-generated) | PK | Unique identifier for the notification. |
| user_id | BIGINT | FK → users.id | User who receives the notification. |
| type | VARCHAR(30) | | Notification kind: `PRICE_HIKE` or `POSSIBLE_CANCELLATION`. |
| message | TEXT | | Notification text shown to the user. |
| sent_at | TIMESTAMP | | When the notification was created and sent. |
| is_read | BOOLEAN | | Whether the user has read the notification (default `false`). |

---

## Relationships Summary

| Parent Table | Child Table | Via Column |
|---|---|---|
| roles | users | users.role_id |
| users | gmail_connections | gmail_connections.user_id |
| users | subscriptions | subscriptions.user_id |
| users | classifier_training_emails | classifier_training_emails.labeled_by |
| users | notifications | notifications.user_id |
| categories | services | services.category_id |
| services | subscriptions | subscriptions.service_id |
| subscriptions | price_log | price_log.subscription_id |
| subscriptions | alerts | alerts.subscription_id |
| price_log | alerts | alerts.price_log_id |

# SubShift Data Dictionary

This document describes every table and column in the SubShift PostgreSQL database, plus its constraints, defaults, indexes, view, function and trigger. It matches `db/schema.sql`, which is the source of truth for the schema.

**Key:** PK = Primary Key, FK = Foreign Key, UQ = Unique.

**Types:** every `id` column is `INT` created as `SERIAL` (auto-generated). Every foreign key column is a plain `INT` so that it matches the primary key it points to.

---

## 1. roles

Defines the access roles a user can have. Loaded with `ADMIN` and `USER` when `schema.sql` runs.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the role. |
| name | VARCHAR(20) | UQ | Role name: `ADMIN` or `USER`. |

---

## 2. users

Stores registered application users and their login credentials. There is a single `users` table for every role, and the role is stored in `role_id`.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the user. |
| name | VARCHAR(100) | | Full name of the user. |
| email | VARCHAR(255) | UQ | Email address, used as the login identifier. |
| password_hash | VARCHAR(255) | | BCrypt hash of the user's password (never plain text). |
| role_id | INT | FK → roles.id | Role assigned to the user. |
| email_alerts_enabled | BOOLEAN | | Whether alert notifications are also emailed to the user over SMTP. Defaults to `TRUE`. |
| created_at | TIMESTAMP | | When the account was created. |

---

## 3. categories

Lookup table of subscription categories. Loaded with five rows when `schema.sql` runs.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the category. |
| name | VARCHAR(50) | UQ | Category name: `Entertainment`, `Productivity`, `Fitness`, `Utilities` or `Other`. |

---

## 4. services

Catalog of subscription services (e.g. Netflix, Spotify) that users can track.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the service. |
| name | VARCHAR(150) | UQ (with category_id) | Name of the service. Unique only together with `category_id`. |
| category_id | INT | FK → categories.id, UQ (with name) | Category the service belongs to. |
| created_at | TIMESTAMP | | When the service was added to the catalog. |

---

## 5. gmail_connections

Stores a user's connected mailbox: Gmail, Outlook or Yahoo Mail. A user can have at most one connection. The table keeps its original name from when only Gmail was supported. Every secret is stored encrypted (AES-256-GCM).

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the connection. |
| user_id | INT | FK → users.id, UQ | User who connected the mailbox. |
| provider | VARCHAR(10) | | `GMAIL`, `OUTLOOK` or `YAHOO`. Defaults to `GMAIL`. |
| mailbox_address | VARCHAR(255) | | The connected address, when known (Yahoo). Empty for Gmail and Outlook. |
| access_token_encrypted | TEXT | | Encrypted OAuth access token (Gmail, Outlook). |
| refresh_token_encrypted | TEXT | | Encrypted OAuth refresh token used to renew access (Gmail, Outlook). Required unless the provider is `YAHOO`. |
| app_password_encrypted | TEXT | | Encrypted Yahoo app password used for IMAP. Required when the provider is `YAHOO`. |
| connected_at | TIMESTAMP | | When the Gmail account was connected. |
| last_synced_at | TIMESTAMP | | When emails were last fetched from this account. Empty until the first sync. |

---

## 6. subscriptions

Links a user to a service they are subscribed to. A user can subscribe to a given service only once.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the subscription. |
| user_id | INT | FK → users.id, UQ (with service_id) | User who owns the subscription. |
| service_id | INT | FK → services.id, UQ (with user_id) | Service being subscribed to. |
| status | VARCHAR(20) | | Subscription state: `ACTIVE` or `CANCELLED`. Defaults to `ACTIVE`. |
| plan_name | VARCHAR(100) | | Plan or tier of the service, for example `Pro` or `Family`. Empty when unknown. The service row holds only the brand, so "Claude", "Claude Pro" and "Claude Plus" are one service with different plans. |
| last_checked_date | DATE | | Last date a price or renewal was recorded for this subscription. Empty until the first check. |
| created_at | TIMESTAMP | | When the subscription record was created. |

---

## 7. price_log

History of price changes for each subscription. A row is added only when the price changes.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the price entry. |
| subscription_id | INT | FK → subscriptions.id | Subscription this price belongs to. |
| price | DECIMAL(10,2) | | Price charged for the subscription. Cannot be negative. |
| date_recorded | DATE | | Date the price was observed. |
| source | VARCHAR(20) | | How the price was captured: `EMAIL_PARSED` or `MANUAL`. |

---

## 8. alerts

Price-change alerts (increases and drops) created automatically by the `trg_flag_price_hike` trigger when a new price differs from the previous one.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the alert. |
| subscription_id | INT | FK → subscriptions.id | Subscription the alert is about. |
| price_log_id | INT | FK → price_log.id | Price log entry that triggered the alert. |
| old_price | DECIMAL(10,2) | | Price before the change. |
| new_price | DECIMAL(10,2) | | Price after the change. Higher than `old_price` for an increase, lower for a drop. |
| detected_at | TIMESTAMP | | When the price change was detected. |
| is_read | BOOLEAN | | Whether the user has read the alert. Defaults to `FALSE`. |

---

## 9. classifier_training_emails

Labeled sample emails used to train the renewal-email classifier.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the training sample. |
| email_text | TEXT | | Full subject and body text of the email. |
| label | VARCHAR(20) | | Classification label: `RENEWAL` or `NOT_RENEWAL`. |
| labeled_by | INT | FK → users.id | User who assigned the label. |
| created_at | TIMESTAMP | | When the sample was added. |

---

## 10. notifications

Messages sent to users about price increases, price drops and possible cancellations.

| Column | Data Type | Key | Description |
|---|---|---|---|
| id | INT (SERIAL) | PK | Unique identifier for the notification. |
| user_id | INT | FK → users.id | User who receives the notification. |
| type | VARCHAR(30) | | Notification kind: `PRICE_HIKE`, `PRICE_DROP` or `POSSIBLE_CANCELLATION`. |
| message | TEXT | | Notification text shown to the user. |
| sent_at | TIMESTAMP | | When the notification was created and sent. |
| is_read | BOOLEAN | | Whether the user has read the notification. Defaults to `FALSE`. |

---

## Relationships Summary

| Parent Table | Child Table | Via Column | On parent delete |
|---|---|---|---|
| roles | users | users.role_id | Blocked |
| users | gmail_connections | gmail_connections.user_id | Child rows deleted |
| users | subscriptions | subscriptions.user_id | Child rows deleted |
| users | classifier_training_emails | classifier_training_emails.labeled_by | Blocked |
| users | notifications | notifications.user_id | Child rows deleted |
| categories | services | services.category_id | Blocked |
| services | subscriptions | subscriptions.service_id | Blocked |
| subscriptions | price_log | price_log.subscription_id | Child rows deleted |
| subscriptions | alerts | alerts.subscription_id | Child rows deleted |
| price_log | alerts | alerts.price_log_id | Child rows deleted |

"Blocked" means PostgreSQL refuses to delete the parent row while child rows still reference it.

---

## Constraints and Defaults

Every column listed above is `NOT NULL` except `subscriptions.last_checked_date`, `subscriptions.plan_name`, `gmail_connections.last_synced_at`, `gmail_connections.mailbox_address` and the three credential columns of `gmail_connections` (a check constraint requires the right one for each provider).

| Table | Column | CHECK constraint | Default |
|---|---|---|---|
| roles | name | Must be `ADMIN` or `USER` | |
| subscriptions | status | Must be `ACTIVE` or `CANCELLED` | `'ACTIVE'` |
| price_log | price | Must be 0 or more | |
| price_log | source | Must be `EMAIL_PARSED` or `MANUAL` | |
| classifier_training_emails | label | Must be `RENEWAL` or `NOT_RENEWAL` | |
| notifications | type | Must be `PRICE_HIKE`, `PRICE_DROP` or `POSSIBLE_CANCELLATION` | |
| gmail_connections | provider | Must be `GMAIL`, `OUTLOOK` or `YAHOO` | `'GMAIL'` |
| users | email_alerts_enabled | | `TRUE` |
| alerts | is_read | | `FALSE` |
| notifications | is_read | | `FALSE` |

Columns named `created_at`, `connected_at`, `detected_at` and `sent_at` default to the current time (`NOW()`).

Unique constraints beyond the primary keys and single-column `UQ` keys above:

| Table | Columns | Meaning |
|---|---|---|
| services | (name, category_id) | A service name can repeat only across different categories. |
| subscriptions | (user_id, service_id) | A user tracks each service at most once. |

---

## Indexes

Primary keys and unique constraints get an index automatically. These are the extra indexes created in `schema.sql`:

| Index | Table | Column |
|---|---|---|
| idx_users_role_id | users | role_id |
| idx_services_category_id | services | category_id |
| idx_subscriptions_user_id | subscriptions | user_id |
| idx_subscriptions_service_id | subscriptions | service_id |
| idx_subscriptions_last_checked_date | subscriptions | last_checked_date |
| idx_price_log_subscription_id | price_log | subscription_id |
| idx_price_log_date_recorded | price_log | date_recorded |
| idx_alerts_subscription_id | alerts | subscription_id |
| idx_notifications_user_id | notifications | user_id |

---

## View: active_subscriptions_overview

Powers the dashboard. It returns one row per `ACTIVE` subscription, joined with its service, its category and its most recent price (rows with no price yet are still included, with empty price columns). The most recent price is the one with the latest `date_recorded`, and if two prices share a date, the newest entry (highest `id`) wins.

| Column | Data Type | Description |
|---|---|---|
| subscription_id | INT | `subscriptions.id`. |
| user_id | INT | Owner of the subscription. |
| service_name | VARCHAR(150) | `services.name`. |
| category_name | VARCHAR(50) | `categories.name`. |
| status | VARCHAR(20) | Always `ACTIVE` in this view. |
| last_checked_date | DATE | `subscriptions.last_checked_date`. |
| current_price | DECIMAL(10,2) | Latest `price_log.price` for the subscription. |
| price_last_updated | DATE | `date_recorded` of that latest price. |

---

## Function: get_user_spend_summary(p_user_id INT)

A PL/pgSQL function (the project's stored procedure) that returns the total monthly spend per category for one user. It sums the latest price of each of the user's `ACTIVE` subscriptions, grouped by category. Active subscriptions that have no price yet are left out.

Example: `SELECT * FROM get_user_spend_summary(1);`

| Returned column | Data Type | Description |
|---|---|---|
| category_name | VARCHAR | Category name. |
| total_spend | DECIMAL | Sum of the latest prices of the user's active subscriptions in that category. |

---

## Trigger: trg_flag_price_hike

Fires `AFTER INSERT` on `price_log`, once for each new row, and runs the function `flag_price_hike()`. The function finds the previous price for the same subscription (the most recent other row, ordered by `date_recorded` and then by `id` so same-day changes are ordered correctly). If there is a previous price and the new price is different, higher or lower, it inserts a row into `alerts` with the old and new prices. Nothing is written for a first price. The view and the spend function use the same ordering for the latest price.

The application never inserts alerts itself. All price-change alerts (hikes and drops) come from this trigger. The trigger and function names were kept from when it only flagged increases.
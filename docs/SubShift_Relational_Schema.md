# SubShift — Relational Schema Diagram
*(Derived from the ER Diagram — see `SubShift_ER_Diagram.png`)*

This shows the logical/relational level: every table with its actual columns, primary keys, and foreign keys — the step that comes **after** the conceptual ER diagram, where relationships become FK columns.

Renders automatically on GitHub (Mermaid support is native in `.md` files).

```mermaid
erDiagram
    ROLES ||--o{ USERS : "has"
    USERS ||--o{ SUBSCRIPTIONS : "owns"
    USERS ||--o| GMAIL_CONNECTIONS : "connects"
    USERS ||--o{ NOTIFICATIONS : "receives"
    USERS ||--o{ CLASSIFIER_TRAINING_EMAILS : "labels"
    CATEGORIES ||--o{ SERVICES : "classifies"
    SERVICES ||--o{ SUBSCRIPTIONS : "subscribed via"
    SUBSCRIPTIONS ||--o{ PRICE_LOG : "has history"
    PRICE_LOG ||--o| ALERTS : "may trigger"
    SUBSCRIPTIONS ||--o{ ALERTS : "flagged on"

    ROLES {
        int id PK
        string name
    }

    USERS {
        int id PK
        string name
        string email
        string password_hash
        int role_id FK
        timestamp created_at
    }

    CATEGORIES {
        int id PK
        string name
    }

    SERVICES {
        int id PK
        string name
        int category_id FK
        timestamp created_at
    }

    GMAIL_CONNECTIONS {
        int id PK
        int user_id FK
        string access_token_encrypted
        string refresh_token_encrypted
        timestamp connected_at
        timestamp last_synced_at
    }

    SUBSCRIPTIONS {
        int id PK
        int user_id FK
        int service_id FK
        string status
        date last_checked_date
        timestamp created_at
    }

    PRICE_LOG {
        int id PK
        int subscription_id FK
        decimal price
        date date_recorded
        string source
    }

    ALERTS {
        int id PK
        int subscription_id FK
        int price_log_id FK
        decimal old_price
        decimal new_price
        timestamp detected_at
        boolean is_read
    }

    CLASSIFIER_TRAINING_EMAILS {
        int id PK
        text email_text
        string label
        int labeled_by FK
        timestamp created_at
    }

    NOTIFICATIONS {
        int id PK
        int user_id FK
        string type
        text message
        timestamp sent_at
        boolean is_read
    }
```

**Primary entities (4+, per rubric):** USERS, SERVICES, SUBSCRIPTIONS, PRICE_LOG
**Relationships (3+, per rubric):** 10 shown above — well over the minimum
**Tables (8+, per rubric):** 10 total

## Notes on cardinality choices
- `USERS ||--o| GMAIL_CONNECTIONS` — one-to-zero-or-one: a user may or may not have connected Gmail yet
- `PRICE_LOG ||--o| ALERTS` — zero-or-one: most price_log rows never trigger an alert (only ones where price increased)
- `ROLES ||--o{ USERS` — one role can apply to many users; this is the RBAC mechanism (single `users` table, role via FK — never separate per-role tables, per the rubric's explicit warning)

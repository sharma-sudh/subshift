-- SubShift — Database Schema
-- PostgreSQL DDL. Run against a fresh database, e.g.: psql -U postgres -d subshift_db -f schema.sql
-- Normalized to 3NF. Every table has a PK, appropriate FKs, and relevant constraints.

-- ============================================================
-- LOOKUP TABLES
-- ============================================================

CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(20) NOT NULL UNIQUE CHECK (name IN ('ADMIN', 'USER'))
);

CREATE TABLE categories (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE
);

-- ============================================================
-- CORE ENTITIES
-- ============================================================

CREATE TABLE users (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role_id         INT NOT NULL REFERENCES roles(id),
    email_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_role_id ON users(role_id);

CREATE TABLE services (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    category_id     INT NOT NULL REFERENCES categories(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (name, category_id)
);

CREATE INDEX idx_services_category_id ON services(category_id);

-- One connected mailbox per user. The table keeps its original name, but it now holds Gmail, Outlook
-- and Yahoo connections (see the provider column). Gmail/Outlook use OAuth tokens; Yahoo uses IMAP with an
-- app password. Every secret is stored encrypted (AES-256-GCM, see TokenEncryptor).
CREATE TABLE gmail_connections (
    id                          SERIAL PRIMARY KEY,
    user_id                     INT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    provider                    VARCHAR(10) NOT NULL DEFAULT 'GMAIL' CHECK (provider IN ('GMAIL', 'OUTLOOK', 'YAHOO')),
    mailbox_address             VARCHAR(255),
    access_token_encrypted      TEXT,
    refresh_token_encrypted     TEXT,
    app_password_encrypted      TEXT,
    connected_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    last_synced_at              TIMESTAMP,
    CONSTRAINT gmail_connections_credentials_check CHECK (
        (provider = 'YAHOO' AND app_password_encrypted IS NOT NULL)
        OR (provider <> 'YAHOO' AND refresh_token_encrypted IS NOT NULL))
);

CREATE TABLE subscriptions (
    id                  SERIAL PRIMARY KEY,
    user_id             INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_id          INT NOT NULL REFERENCES services(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    plan_name           VARCHAR(100),  -- tier of the service, e.g. Pro or Family; the service row is the brand only
    last_checked_date   DATE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, service_id)
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_service_id ON subscriptions(service_id);
CREATE INDEX idx_subscriptions_last_checked_date ON subscriptions(last_checked_date);

CREATE TABLE price_log (
    id                  SERIAL PRIMARY KEY,
    subscription_id     INT NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    price               DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    date_recorded       DATE NOT NULL,
    source              VARCHAR(20) NOT NULL CHECK (source IN ('EMAIL_PARSED', 'MANUAL'))
);

CREATE INDEX idx_price_log_subscription_id ON price_log(subscription_id);
CREATE INDEX idx_price_log_date_recorded ON price_log(date_recorded);

CREATE TABLE alerts (
    id                  SERIAL PRIMARY KEY,
    subscription_id     INT NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    price_log_id        INT NOT NULL REFERENCES price_log(id) ON DELETE CASCADE,
    old_price           DECIMAL(10, 2) NOT NULL,
    new_price           DECIMAL(10, 2) NOT NULL,
    detected_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    is_read             BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_alerts_subscription_id ON alerts(subscription_id);

CREATE TABLE classifier_training_emails (
    id              SERIAL PRIMARY KEY,
    email_text      TEXT NOT NULL,
    label           VARCHAR(20) NOT NULL CHECK (label IN ('RENEWAL', 'NOT_RENEWAL')),
    labeled_by      INT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
    id              SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(30) NOT NULL CHECK (type IN ('PRICE_HIKE', 'PRICE_DROP', 'POSSIBLE_CANCELLATION')),
    message         TEXT NOT NULL,
    sent_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    is_read         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);

-- ============================================================
-- SEED DATA (required lookups — insert before anything else)
-- ============================================================

INSERT INTO roles (name) VALUES ('ADMIN'), ('USER');

INSERT INTO categories (name) VALUES
    ('Entertainment'),
    ('Productivity'),
    ('Fitness'),
    ('Utilities'),
    ('Other');

-- ============================================================
-- VIEW — mandatory-adjacent, powers the dashboard
-- ============================================================

CREATE VIEW active_subscriptions_overview AS
SELECT
    s.id                AS subscription_id,
    s.user_id,
    sv.name             AS service_name,
    c.name              AS category_name,
    s.status,
    s.last_checked_date,
    pl.price            AS current_price,
    pl.date_recorded    AS price_last_updated,
    s.plan_name         AS plan_name
FROM subscriptions s
JOIN services sv        ON s.service_id = sv.id
JOIN categories c        ON sv.category_id = c.id
LEFT JOIN LATERAL (
    SELECT price, date_recorded
    FROM price_log
    WHERE subscription_id = s.id
    ORDER BY date_recorded DESC, id DESC
    LIMIT 1
) pl ON TRUE
WHERE s.status = 'ACTIVE';

-- ============================================================
-- STORED PROCEDURE — mandatory, used for spend summary reporting
-- ============================================================

CREATE OR REPLACE FUNCTION get_user_spend_summary(p_user_id INT)
RETURNS TABLE (
    category_name   VARCHAR,
    total_spend     DECIMAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        c.name,
        SUM(pl.price)
    FROM subscriptions s
    JOIN services sv ON s.service_id = sv.id
    JOIN categories c ON sv.category_id = c.id
    JOIN LATERAL (
        SELECT price
        FROM price_log
        WHERE subscription_id = s.id
        ORDER BY date_recorded DESC, id DESC
        LIMIT 1
    ) pl ON TRUE
    WHERE s.user_id = p_user_id AND s.status = 'ACTIVE'
    GROUP BY c.name;
END;
$$ LANGUAGE plpgsql;

-- Example call: SELECT * FROM get_user_spend_summary(1);

-- ============================================================
-- TRIGGER — mandatory, auto-flags price changes (increases and drops) on insert
-- ============================================================

CREATE OR REPLACE FUNCTION flag_price_hike()
RETURNS TRIGGER AS $$
DECLARE
    v_previous_price DECIMAL(10, 2);
BEGIN
    SELECT price INTO v_previous_price
    FROM price_log
    WHERE subscription_id = NEW.subscription_id
      AND id != NEW.id
    ORDER BY date_recorded DESC, id DESC
    LIMIT 1;

    IF v_previous_price IS NOT NULL AND NEW.price <> v_previous_price THEN
        INSERT INTO alerts (subscription_id, price_log_id, old_price, new_price)
        VALUES (NEW.subscription_id, NEW.id, v_previous_price, NEW.price);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_flag_price_hike
AFTER INSERT ON price_log
FOR EACH ROW
EXECUTE FUNCTION flag_price_hike();

-- ============================================================
-- STORED PROCEDURE — marks every unread price-change alert of one user as read in a single statement
-- (called by the backend endpoint PATCH /api/alerts/read-all)
-- ============================================================

CREATE OR REPLACE PROCEDURE mark_all_alerts_read(p_user_id INT)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE alerts a
    SET is_read = TRUE
    FROM subscriptions s
    WHERE a.subscription_id = s.id
      AND s.user_id = p_user_id
      AND a.is_read = FALSE;
END;
$$;

-- Example call: CALL mark_all_alerts_read(1);

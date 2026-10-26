-- Patch for an EXISTING database (the Render one). Safe to run more than once.
-- Changes for the review feedback:
-- 1) subscriptions.plan_name: "Claude", "Claude Pro" and "Claude Plus" are one service with different plans.
-- 2) users.email_alerts_enabled: per-user switch for alert emails sent over SMTP.
-- 3) gmail_connections: now also holds Outlook (OAuth) and Yahoo (IMAP + app password) connections.
-- 4) active_subscriptions_overview shows the plan (new column added at the END, so CREATE OR REPLACE works).
-- Run: psql "<connection string>" -f db/patches/2026-10-25_review_changes.sql

BEGIN;

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_name VARCHAR(100);

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE gmail_connections ADD COLUMN IF NOT EXISTS provider VARCHAR(10) NOT NULL DEFAULT 'GMAIL';
ALTER TABLE gmail_connections ADD COLUMN IF NOT EXISTS mailbox_address VARCHAR(255);
ALTER TABLE gmail_connections ADD COLUMN IF NOT EXISTS app_password_encrypted TEXT;
ALTER TABLE gmail_connections ALTER COLUMN access_token_encrypted DROP NOT NULL;
ALTER TABLE gmail_connections ALTER COLUMN refresh_token_encrypted DROP NOT NULL;
ALTER TABLE gmail_connections DROP CONSTRAINT IF EXISTS gmail_connections_provider_check;
ALTER TABLE gmail_connections
    ADD CONSTRAINT gmail_connections_provider_check CHECK (provider IN ('GMAIL', 'OUTLOOK', 'YAHOO'));
ALTER TABLE gmail_connections DROP CONSTRAINT IF EXISTS gmail_connections_credentials_check;
ALTER TABLE gmail_connections
    ADD CONSTRAINT gmail_connections_credentials_check CHECK (
        (provider = 'YAHOO' AND app_password_encrypted IS NOT NULL)
        OR (provider <> 'YAHOO' AND refresh_token_encrypted IS NOT NULL));

CREATE OR REPLACE VIEW active_subscriptions_overview AS
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

COMMIT;

-- OPTIONAL clean-up of services that were already split before this change (for example "Claude" and
-- "Claude Pro" as two rows). Review first, then merge by hand:
--   SELECT id, name FROM services ORDER BY lower(name);
-- Moving a subscription to the canonical service (replace the ids), then keep the old name as its plan:
--   UPDATE subscriptions SET service_id = <canonical id>, plan_name = 'Pro' WHERE service_id = <duplicate id>;
--   DELETE FROM services WHERE id = <duplicate id>;
-- If one user holds BOTH rows, delete one of those subscriptions first (user_id + service_id is unique).

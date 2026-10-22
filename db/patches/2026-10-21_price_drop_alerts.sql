-- Patch for an EXISTING database (the Render one). Safe to run more than once.
-- 1) Alerts now fire on any price change (increase or drop), not only increases.
-- 2) notifications.type accepts PRICE_DROP.
-- 3) "Latest price" now breaks same-day ties by newest id, so the dashboard view and
--    the spend function agree with the Java code (this fixes the dashboard showing an
--    old price after a same-day price change).
-- Run: psql "<connection string>" -f db/patches/2026-10-21_price_drop_alerts.sql

BEGIN;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('PRICE_HIKE', 'PRICE_DROP', 'POSSIBLE_CANCELLATION'));

CREATE OR REPLACE VIEW active_subscriptions_overview AS
SELECT
    s.id                AS subscription_id,
    s.user_id,
    sv.name             AS service_name,
    c.name              AS category_name,
    s.status,
    s.last_checked_date,
    pl.price            AS current_price,
    pl.date_recorded    AS price_last_updated
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

    -- Function name kept for compatibility; it now flags drops as well as hikes.
    IF v_previous_price IS NOT NULL AND NEW.price <> v_previous_price THEN
        INSERT INTO alerts (subscription_id, price_log_id, old_price, new_price)
        VALUES (NEW.subscription_id, NEW.id, v_previous_price, NEW.price);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMIT;

-- Database tests for the trigger and the stored procedure. Runs inside one transaction and rolls back,
-- so it leaves no data behind. Fails (non-zero exit) if any assertion is wrong when run with:
--   psql -U postgres -d subshift_db -v ON_ERROR_STOP=1 -f db/tests/trigger_and_procedure_test.sql
BEGIN;

DO $$
DECLARE
    v_user INT;
    v_service INT;
    v_sub INT;
    v_alerts INT;
    v_unread INT;
BEGIN
    INSERT INTO users (name, email, password_hash, role_id)
    VALUES ('Test User', 'db-test@example.com', 'x', (SELECT id FROM roles WHERE name = 'USER'))
    RETURNING id INTO v_user;
    INSERT INTO services (name, category_id)
    VALUES ('DB Test Service', (SELECT id FROM categories WHERE name = 'Other'))
    RETURNING id INTO v_service;
    INSERT INTO subscriptions (user_id, service_id) VALUES (v_user, v_service) RETURNING id INTO v_sub;

    -- First price: nothing to compare with, so no alert.
    INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES (v_sub, 199.00, '2026-01-01', 'MANUAL');
    SELECT count(*) INTO v_alerts FROM alerts WHERE subscription_id = v_sub;
    IF v_alerts <> 0 THEN RAISE EXCEPTION 'TRIGGER FAIL: first price created % alerts, expected 0', v_alerts; END IF;

    -- Same price again: still no alert.
    INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES (v_sub, 199.00, '2026-02-01', 'MANUAL');
    SELECT count(*) INTO v_alerts FROM alerts WHERE subscription_id = v_sub;
    IF v_alerts <> 0 THEN RAISE EXCEPTION 'TRIGGER FAIL: unchanged price created % alerts, expected 0', v_alerts; END IF;

    -- Price hike and price drop: one alert each.
    INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES (v_sub, 249.00, '2026-03-01', 'MANUAL');
    INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES (v_sub, 149.00, '2026-04-01', 'MANUAL');
    SELECT count(*) INTO v_alerts FROM alerts WHERE subscription_id = v_sub;
    IF v_alerts <> 2 THEN RAISE EXCEPTION 'TRIGGER FAIL: hike + drop created % alerts, expected 2', v_alerts; END IF;

    -- Stored procedure marks them all read, and only for this user.
    SELECT count(*) INTO v_unread FROM alerts WHERE subscription_id = v_sub AND NOT is_read;
    IF v_unread <> 2 THEN RAISE EXCEPTION 'PROCEDURE FAIL: expected 2 unread before the call, got %', v_unread; END IF;
    CALL mark_all_alerts_read(v_user);
    SELECT count(*) INTO v_unread FROM alerts WHERE subscription_id = v_sub AND NOT is_read;
    IF v_unread <> 0 THEN RAISE EXCEPTION 'PROCEDURE FAIL: % alerts still unread after the call', v_unread; END IF;

    -- The spend function returns the latest price per active subscription.
    IF (SELECT total_spend FROM get_user_spend_summary(v_user) WHERE category_name = 'Other') <> 149.00 THEN
        RAISE EXCEPTION 'FUNCTION FAIL: spend summary should use the latest price (149.00)';
    END IF;

    RAISE NOTICE 'All database tests passed';
END;
$$;

ROLLBACK;

-- Adds the mark_all_alerts_read stored procedure to an existing database.
-- Safe to run more than once (CREATE OR REPLACE).
-- Apply BEFORE deploying the backend version that calls it:
--   psql -h <host> -U <user> -d subshift_db -v ON_ERROR_STOP=1 -f db/patches/2026-10-30_stored_procedure.sql

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

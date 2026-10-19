-- SubShift demo data. FOR DEVELOPMENT AND DEMOS ONLY. Never load this into a real deployment.
--
-- Run once on a freshly created database, AFTER db/schema.sql:
--   psql -U postgres -d subshift_db -f db/demo_seed.sql
--
-- Creates two accounts (password for both: Demo@12345):
--   demo@subshift.test   (USER)   9 subscriptions with price history, alerts and notifications
--   admin@subshift.test  (ADMIN)  2 subscriptions
-- Every table except classifier_training_emails and gmail_connections gets rows. Alerts are produced
-- by the real price_log trigger, so loading this script also exercises that trigger.
-- The script is ASCII only, so it loads cleanly on Windows too.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE email IN ('demo@subshift.test', 'admin@subshift.test')) THEN
        RAISE EXCEPTION 'Demo data is already loaded. Use a fresh database.';
    END IF;
END $$;

INSERT INTO users (name, email, password_hash, role_id) VALUES
    ('Demo User',  'demo@subshift.test',  '$2b$10$ARlBnle8aO9tyVqeDu/8cOH9yif.QiUNap4zTUs6tTpqWKM3/uTYq', (SELECT id FROM roles WHERE name = 'USER')),
    ('Demo Admin', 'admin@subshift.test', '$2b$10$ARlBnle8aO9tyVqeDu/8cOH9yif.QiUNap4zTUs6tTpqWKM3/uTYq', (SELECT id FROM roles WHERE name = 'ADMIN'));

INSERT INTO services (name, category_id) VALUES
    ('Netflix', (SELECT id FROM categories WHERE name = 'Entertainment')),
    ('Spotify', (SELECT id FROM categories WHERE name = 'Entertainment')),
    ('Amazon Prime', (SELECT id FROM categories WHERE name = 'Entertainment')),
    ('YouTube Premium', (SELECT id FROM categories WHERE name = 'Entertainment')),
    ('Google One', (SELECT id FROM categories WHERE name = 'Utilities')),
    ('Microsoft 365', (SELECT id FROM categories WHERE name = 'Productivity')),
    ('Notion', (SELECT id FROM categories WHERE name = 'Productivity')),
    ('Cult.fit', (SELECT id FROM categories WHERE name = 'Fitness')),
    ('Duolingo', (SELECT id FROM categories WHERE name = 'Other'))
ON CONFLICT (name, category_id) DO NOTHING;

-- Subscriptions
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'Netflix'), 'ACTIVE', DATE '2026-10-05');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'Spotify'), 'ACTIVE', DATE '2026-10-14');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'Amazon Prime'), 'ACTIVE', DATE '2026-10-03');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'YouTube Premium'), 'ACTIVE', DATE '2026-10-12');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'Google One'), 'ACTIVE', DATE '2026-10-03');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'Microsoft 365'), 'ACTIVE', DATE '2026-10-12');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'Notion'), 'ACTIVE', DATE '2026-10-11');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'Cult.fit'), 'CANCELLED', DATE '2026-03-11');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), (SELECT id FROM services WHERE name = 'Duolingo'), 'ACTIVE', DATE '2026-06-20');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'admin@subshift.test'), (SELECT id FROM services WHERE name = 'Netflix'), 'ACTIVE', DATE '2026-10-05');
INSERT INTO subscriptions (user_id, service_id, status, last_checked_date) VALUES ((SELECT id FROM users WHERE email = 'admin@subshift.test'), (SELECT id FROM services WHERE name = 'Spotify'), 'ACTIVE', DATE '2026-10-14');

-- Price history. One INSERT per price, oldest first: the trigger compares each new row with the previous one,
-- and a multi-row INSERT would skip some price-hike alerts.
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Netflix'), 499, DATE '2024-03-05', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Netflix'), 549, DATE '2025-06-05', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Netflix'), 649, DATE '2026-10-05', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Spotify'), 119, DATE '2024-02-14', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Spotify'), 139, DATE '2025-08-14', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Amazon Prime'), 179, DATE '2024-05-03', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Amazon Prime'), 299, DATE '2025-05-03', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'YouTube Premium'), 129, DATE '2024-06-25', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'YouTube Premium'), 149, DATE '2025-10-25', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Google One'), 130, DATE '2024-01-03', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Google One'), 160, DATE '2026-03-03', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Microsoft 365'), 489, DATE '2024-07-12', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Microsoft 365'), 529, DATE '2026-01-12', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Notion'), 849, DATE '2025-02-11', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Cult.fit'), 1499, DATE '2025-01-11', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Cult.fit'), 1699, DATE '2025-11-11', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'demo@subshift.test' AND sv.name = 'Duolingo'), 749, DATE '2025-03-20', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'admin@subshift.test' AND sv.name = 'Netflix'), 549, DATE '2025-06-05', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'admin@subshift.test' AND sv.name = 'Netflix'), 649, DATE '2026-10-05', 'MANUAL');
INSERT INTO price_log (subscription_id, price, date_recorded, source) VALUES ((SELECT sub.id FROM subscriptions sub JOIN users us ON us.id = sub.user_id JOIN services sv ON sv.id = sub.service_id WHERE us.email = 'admin@subshift.test' AND sv.name = 'Spotify'), 139, DATE '2025-08-14', 'MANUAL');

-- The trigger stamps alerts with NOW(). Back-date them to the day each price took effect,
-- and mark the older ones as read so the demo shows both read and unread alerts.
UPDATE alerts a SET detected_at = p.date_recorded + TIME '09:00' FROM price_log p WHERE a.price_log_id = p.id;
UPDATE alerts SET is_read = TRUE WHERE detected_at < TIMESTAMP '2026-06-01';

-- Notifications (the app normally creates these during an email sync)
INSERT INTO notifications (user_id, type, message, sent_at, is_read) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), 'PRICE_HIKE', 'Netflix price increased from ' || chr(8377) || '549 to ' || chr(8377) || '649', TIMESTAMP '2026-10-05 09:05', FALSE);
INSERT INTO notifications (user_id, type, message, sent_at, is_read) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), 'PRICE_HIKE', 'Google One price increased from ' || chr(8377) || '130 to ' || chr(8377) || '160', TIMESTAMP '2026-03-03 09:05', TRUE);
INSERT INTO notifications (user_id, type, message, sent_at, is_read) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), 'PRICE_HIKE', 'Microsoft 365 price increased from ' || chr(8377) || '489 to ' || chr(8377) || '529', TIMESTAMP '2026-01-12 09:05', TRUE);
INSERT INTO notifications (user_id, type, message, sent_at, is_read) VALUES ((SELECT id FROM users WHERE email = 'demo@subshift.test'), 'POSSIBLE_CANCELLATION', 'No renewal email seen for Duolingo in over 60 days. Did you cancel it?', TIMESTAMP '2026-09-01 08:00', FALSE);

COMMIT;

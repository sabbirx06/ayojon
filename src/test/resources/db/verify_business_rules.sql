-- =====================================================================
-- Ayojon — database self-test
--
-- Proves that the constraints and triggers from V1–V3 accept valid data
-- and reject invalid data. Everything runs inside one transaction that is
-- rolled back at the end, so it is safe to run against any database
-- (local Postgres, Supabase SQL editor, ...) after the migrations.
--
-- Success: the last message printed is  "ALL DATABASE CHECKS PASSED".
-- Failure: the script stops with an exception naming the broken rule.
-- =====================================================================

BEGIN;

CREATE TEMP TABLE t_ids (k TEXT PRIMARY KEY, v BIGINT) ON COMMIT DROP;

-- Runs p_sql and fails the test unless it raises an error.
CREATE FUNCTION pg_temp.expect_error(p_rule TEXT, p_sql TEXT) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'ok   — rejected: %', p_rule;
        RETURN;
    END;
    RAISE EXCEPTION 'FAILED — database accepted invalid data: %', p_rule;
END $$;

CREATE FUNCTION pg_temp.expect(p_rule TEXT, p_ok BOOLEAN) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    IF p_ok IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'FAILED — %', p_rule;
    END IF;
    RAISE NOTICE 'ok   — %', p_rule;
END $$;

CREATE FUNCTION pg_temp.id(p_key TEXT) RETURNS BIGINT
LANGUAGE sql AS $$ SELECT v FROM t_ids WHERE k = p_key $$;

-- ---------------------------------------------------------------------
-- Fixtures
-- ---------------------------------------------------------------------
WITH u AS (
    INSERT INTO users (name, email, password_hash, role) VALUES
        ('T Admin', 'test.admin@ayojon.test', 'x', 'ADMIN'),
        ('T Org',   'test.org@ayojon.test',   'x', 'ORGANIZER'),
        ('T User',  'test.user@ayojon.test',  'x', 'USER'),
        ('T User2', 'test.user2@ayojon.test', 'x', 'USER')
    RETURNING email, user_id
)
INSERT INTO t_ids SELECT split_part(email, '@', 1), user_id FROM u;

INSERT INTO area (area_name) VALUES ('Test Area');
INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
VALUES ((SELECT area_id FROM area WHERE area_name = 'Test Area'), 'Test Hall', 'Test Road', 23.75, 90.39);
INSERT INTO event_category (category_name) VALUES ('Test Category');
INSERT INTO badge (badge_name, milestone_points) VALUES ('Test Badge', 999999);

INSERT INTO t_ids VALUES ('venue', (SELECT venue_id FROM venue WHERE venue_name = 'Test Hall')),
                         ('cat',   (SELECT category_id FROM event_category WHERE category_name = 'Test Category'));

WITH e AS (
    INSERT INTO event (organizer_id, category_id, venue_id, title, start_time, end_time, check_in_points)
    VALUES (pg_temp.id('test.org'), pg_temp.id('cat'), pg_temp.id('venue'), 'Test Event',
            now() + INTERVAL '2 days', now() + INTERVAL '2 days 3 hours', 40)
    RETURNING event_id
)
INSERT INTO t_ids SELECT 'event', event_id FROM e;

DO $$
DECLARE
    ev  BIGINT := pg_temp.id('event');
    adm BIGINT := pg_temp.id('test.admin');
    org BIGINT := pg_temp.id('test.org');
    usr BIGINT := pg_temp.id('test.user');
    us2 BIGINT := pg_temp.id('test.user2');
    col BIGINT;
    itn BIGINT;
    vch BIGINT;
    rep BIGINT;
BEGIN
    -- ---------------- users ----------------
    PERFORM pg_temp.expect_error('duplicate email (case-insensitive)',
        $q$INSERT INTO users (name, email, password_hash) VALUES ('Dup', 'TEST.USER@ayojon.test', 'x')$q$);
    PERFORM pg_temp.expect_error('unknown role',
        $q$INSERT INTO users (name, email, password_hash, role) VALUES ('R', 'r@ayojon.test', 'x', 'SUPERUSER')$q$);

    -- ---------------- events (F16) ----------------
    PERFORM pg_temp.expect('new events start as PENDING_REVIEW',
        (SELECT status = 'PENDING_REVIEW' FROM event WHERE event_id = ev));
    PERFORM pg_temp.expect_error('plain USER cannot own an event', format(
        $q$INSERT INTO event (organizer_id, category_id, venue_id, title, start_time, end_time)
           VALUES (%s, %s, %s, 'X', now(), now() + INTERVAL '1 hour')$q$, usr, pg_temp.id('cat'), pg_temp.id('venue')));
    PERFORM pg_temp.expect_error('end_time must be after start_time', format(
        $q$INSERT INTO event (organizer_id, category_id, venue_id, title, start_time, end_time)
           VALUES (%s, %s, %s, 'X', now(), now() - INTERVAL '1 hour')$q$, org, pg_temp.id('cat'), pg_temp.id('venue')));
    PERFORM pg_temp.expect_error('negative ticket price', format(
        $q$INSERT INTO ticket_tier (event_id, tier_name, price) VALUES (%s, 'Bad', -1)$q$, ev));
    PERFORM pg_temp.expect_error('two posters for one event', format(
        $q$INSERT INTO event_media (event_id, media_url, media_type) VALUES (%1$s, 'a', 'POSTER'), (%1$s, 'b', 'POSTER')$q$, ev));
    INSERT INTO ticket_tier (event_id, tier_name, price) VALUES (ev, 'General', 250);

    -- ---------------- moderation (F18) ----------------
    PERFORM pg_temp.expect_error('check-in before the event is published', format(
        $q$INSERT INTO attendance (user_id, event_id) VALUES (%s, %s)$q$, usr, ev));
    PERFORM pg_temp.expect_error('non-admin cannot review submissions', format(
        $q$INSERT INTO event_submission_review (event_id, admin_id, decision) VALUES (%s, %s, 'APPROVED')$q$, ev, org));
    PERFORM pg_temp.expect_error('rejection without a comment', format(
        $q$INSERT INTO event_submission_review (event_id, admin_id, decision) VALUES (%s, %s, 'REJECTED')$q$, ev, adm));

    INSERT INTO event_submission_review (event_id, admin_id, decision, comment)
    VALUES (ev, adm, 'REVISION_REQUESTED', 'Add a ticket link');
    PERFORM pg_temp.expect('revision request moves event to REVISION_REQUESTED',
        (SELECT status = 'REVISION_REQUESTED' FROM event WHERE event_id = ev));
    PERFORM pg_temp.expect_error('cannot review an event that is not pending', format(
        $q$INSERT INTO event_submission_review (event_id, admin_id, decision) VALUES (%s, %s, 'APPROVED')$q$, ev, adm));

    UPDATE event SET status = 'PENDING_REVIEW', ticket_url = 'https://t.example', updated_by = org WHERE event_id = ev;
    INSERT INTO event_submission_review (event_id, admin_id, decision) VALUES (ev, adm, 'APPROVED');
    PERFORM pg_temp.expect('approval publishes the event and stamps published_at',
        (SELECT status = 'PUBLISHED' AND published_at IS NOT NULL FROM event WHERE event_id = ev));
    PERFORM pg_temp.expect('organizer is notified about every decision',
        (SELECT count(*) = 2 FROM notification WHERE user_id = org AND notification_type = 'SUBMISSION_STATUS'));

    -- ---------------- verification (F7) ----------------
    PERFORM pg_temp.expect('event is unverified until an admin verifies it', NOT event_is_verified(ev));
    PERFORM pg_temp.expect_error('VERIFIED decision needs a source_url', format(
        $q$INSERT INTO event_verification (event_id, admin_id, decision) VALUES (%s, %s, 'VERIFIED')$q$, ev, adm));
    PERFORM pg_temp.expect_error('non-admin cannot verify', format(
        $q$INSERT INTO event_verification (event_id, admin_id, decision, source_url) VALUES (%s, %s, 'VERIFIED', 'u')$q$, ev, org));

    -- ---------------- check-ins, points, badges (F3) ----------------
    INSERT INTO attendance (user_id, event_id) VALUES (us2, ev);
    PERFORM pg_temp.expect('check-in at an unverified event earns 0 points',
        (SELECT points_awarded = 0 FROM attendance WHERE user_id = us2 AND event_id = ev));

    INSERT INTO event_verification (event_id, admin_id, decision, source_url) VALUES (ev, adm, 'VERIFIED', 'https://official.example');
    PERFORM pg_temp.expect('latest VERIFIED decision makes the event verified', event_is_verified(ev));

    UPDATE badge SET milestone_points = 40 WHERE badge_name = 'Test Badge';
    INSERT INTO attendance (user_id, event_id) VALUES (usr, ev);
    PERFORM pg_temp.expect('check-in at a verified event awards check_in_points and updates users.points',
        (SELECT points = 40 FROM users WHERE user_id = usr));
    PERFORM pg_temp.expect('reaching a milestone grants the badge and notifies',
        EXISTS (SELECT 1 FROM user_badge ub JOIN badge b USING (badge_id) WHERE ub.user_id = usr AND b.badge_name = 'Test Badge')
        AND EXISTS (SELECT 1 FROM notification WHERE user_id = usr AND notification_type = 'BADGE_EARNED'));
    PERFORM pg_temp.expect_error('double check-in', format(
        $q$INSERT INTO attendance (user_id, event_id) VALUES (%s, %s)$q$, usr, ev));

    -- ---------------- reviews (F15) ----------------
    PERFORM pg_temp.expect_error('review without attending', format(
        $q$INSERT INTO review (user_id, event_id, rating) VALUES (%s, %s, 5)$q$, org, ev));
    PERFORM pg_temp.expect_error('rating outside 1..5', format(
        $q$INSERT INTO review (user_id, event_id, rating) VALUES (%s, %s, 6)$q$, usr, ev));
    INSERT INTO review (user_id, event_id, rating, review_text) VALUES (usr, ev, 4, 'Nice');
    PERFORM pg_temp.expect_error('second review by the same user', format(
        $q$INSERT INTO review (user_id, event_id, rating) VALUES (%s, %s, 3)$q$, usr, ev));

    -- ---------------- collections & bookmarks (F9, F8) ----------------
    INSERT INTO collection (user_id, collection_name, collection_type, is_private)
    VALUES (usr, 'Test Wishlist', 'WISHLIST', TRUE) RETURNING collection_id INTO col;
    INSERT INTO collection_item (collection_id, event_id) VALUES (col, ev);
    PERFORM pg_temp.expect('adding to a collection auto-bookmarks the event',
        EXISTS (SELECT 1 FROM bookmark WHERE user_id = usr AND event_id = ev));
    PERFORM pg_temp.expect_error('duplicate collection name for the same user', format(
        $q$INSERT INTO collection (user_id, collection_name) VALUES (%s, 'Test Wishlist')$q$, usr));
    PERFORM pg_temp.expect_error('collection item in a non-existent collection', format(
        $q$INSERT INTO collection_item (collection_id, event_id, user_id) VALUES (%s, %s, %s)$q$, col + 999999, ev, us2));
    PERFORM pg_temp.expect('trending view counts this week''s bookmarks',
        EXISTS (SELECT 1 FROM v_trending_events WHERE event_id = ev AND weekly_bookmarks >= 1));

    -- ---------------- itinerary (F10, F11, F13) ----------------
    INSERT INTO itinerary (user_id, title, start_date, end_date)
    VALUES (usr, 'Test Weekend', (now() AT TIME ZONE 'Asia/Dhaka')::date, (now() AT TIME ZONE 'Asia/Dhaka')::date + 3)
    RETURNING itinerary_id INTO itn;
    INSERT INTO itinerary_item (itinerary_id, event_id) VALUES (itn, ev);
    PERFORM pg_temp.expect('itinerary shows up in the calendar export',
        EXISTS (SELECT 1 FROM v_calendar_export WHERE itinerary_id = itn AND uid = 'ayojon-event-' || ev || '@ayojon'));
    PERFORM pg_temp.expect_error('itinerary longer than a week', format(
        $q$INSERT INTO itinerary (user_id, title, start_date, end_date) VALUES (%s, 'Long', current_date, current_date + 10)$q$, usr));
    PERFORM pg_temp.expect_error('planned slot outside the event''s own times', format(
        $q$UPDATE itinerary_item SET planned_start = now() - INTERVAL '1 day' WHERE itinerary_id = %s$q$, itn));
    PERFORM pg_temp.expect('find_overlapping_events detects a clashing slot at the same venue',
        EXISTS (SELECT 1 FROM find_overlapping_events(now() + INTERVAL '2 days 1 hour', now() + INTERVAL '2 days 2 hours', pg_temp.id('venue'))
                 WHERE event_id = ev));

    -- ---------------- search & nearby (F5, F1) ----------------
    PERFORM pg_temp.expect('search finds the event by keyword + area + price',
        EXISTS (SELECT 1 FROM search_events('test', NULL, NULL,
                    (SELECT area_id FROM area WHERE area_name = 'Test Area'), NULL, 100, 300) WHERE event_id = ev));
    PERFORM pg_temp.expect('nearby search finds the event within 1 km',
        EXISTS (SELECT 1 FROM events_nearby(23.751, 90.391, 1) WHERE event_id = ev));
    PERFORM pg_temp.expect('nearby search excludes it at 0.05 km',
        NOT EXISTS (SELECT 1 FROM events_nearby(23.76, 90.40, 0.05) WHERE event_id = ev));

    -- ---------------- follows, audit trail, cancellation (F12, F17) ----------------
    INSERT INTO event_follow (user_id, event_id) VALUES (us2, ev);
    UPDATE event SET start_time = start_time + INTERVAL '30 min', updated_by = org WHERE event_id = ev;
    PERFORM pg_temp.expect('rescheduling writes the audit log with the editor',
        EXISTS (SELECT 1 FROM event_change_log WHERE event_id = ev AND change_type = 'RESCHEDULED' AND changed_by = org));
    PERFORM pg_temp.expect('followers are notified of the reschedule',
        EXISTS (SELECT 1 FROM notification WHERE user_id = us2 AND event_id = ev AND notification_type = 'EVENT_UPDATED'));
    PERFORM pg_temp.expect_error('cancelling without a reason', format(
        $q$UPDATE event SET status = 'CANCELLED' WHERE event_id = %s$q$, ev));
    UPDATE event SET status = 'CANCELLED', cancellation_reason = 'Storm warning', updated_by = org WHERE event_id = ev;
    PERFORM pg_temp.expect('followers are notified of the cancellation',
        EXISTS (SELECT 1 FROM notification WHERE user_id = us2 AND event_id = ev AND notification_type = 'EVENT_CANCELLED'));
    PERFORM pg_temp.expect('public change log shows the cancellation',
        EXISTS (SELECT 1 FROM v_public_event_change_log WHERE event_id = ev AND change_type = 'CANCELLED'));

    -- ---------------- disputes (F14) ----------------
    INSERT INTO dispute_report (reporter_id, event_id, reason_id, details)
    VALUES (us2, ev, (SELECT reason_id FROM dispute_reason WHERE reason_code = 'SPAM'), 'This listing looks like spam.')
    RETURNING report_id INTO rep;
    PERFORM pg_temp.expect_error('duplicate open report', format(
        $q$INSERT INTO dispute_report (reporter_id, event_id, reason_id, details)
           VALUES (%s, %s, (SELECT reason_id FROM dispute_reason WHERE reason_code = 'SPAM'), 'Spam again, really.')$q$, us2, ev));
    PERFORM pg_temp.expect_error('non-admin resolving a report', format(
        $q$UPDATE dispute_report SET status = 'RESOLVED', resolved_by = %s WHERE report_id = %s$q$, org, rep));
    UPDATE dispute_report SET status = 'DISMISSED', resolved_by = adm WHERE report_id = rep;
    PERFORM pg_temp.expect('resolving stamps resolved_at and notifies the reporter',
        (SELECT resolved_at IS NOT NULL FROM dispute_report WHERE report_id = rep)
        AND EXISTS (SELECT 1 FROM notification WHERE user_id = us2 AND notification_type = 'REPORT_UPDATE'));

    -- ---------------- vouchers (F4) ----------------
    INSERT INTO sponsor (sponsor_name) VALUES ('Test Sponsor');
    INSERT INTO ad_campaign (sponsor_id, campaign_name, starts_at, ends_at)
    VALUES ((SELECT sponsor_id FROM sponsor WHERE sponsor_name = 'Test Sponsor'), 'Test Campaign', now() - INTERVAL '1 day', now() + INTERVAL '1 day');
    INSERT INTO voucher (campaign_id, promo_code, discount_percent, min_points_required, total_quantity, valid_until)
    VALUES ((SELECT campaign_id FROM ad_campaign WHERE campaign_name = 'Test Campaign'), 'TEST-ONE', 10, 40, 1, now() + INTERVAL '1 day')
    RETURNING voucher_id INTO vch;
    PERFORM pg_temp.expect_error('lower-case promo code', format(
        $q$INSERT INTO voucher (campaign_id, promo_code, discount_percent, valid_until)
           VALUES ((SELECT campaign_id FROM voucher WHERE voucher_id = %s), 'lower', 10, now() + INTERVAL '1 day')$q$, vch));
    PERFORM pg_temp.expect_error('claim without enough points', format(
        $q$INSERT INTO voucher_claim (user_id, voucher_id) VALUES (%s, %s)$q$, us2, vch));
    INSERT INTO voucher_claim (user_id, voucher_id) VALUES (usr, vch);
    PERFORM pg_temp.expect('voucher with 1 unit is no longer claimable',
        (SELECT NOT is_claimable FROM v_voucher_availability WHERE voucher_id = vch));
    UPDATE users SET points = 100 WHERE user_id = us2;   -- bypass points to test stock limit
    PERFORM pg_temp.expect_error('claim beyond total_quantity', format(
        $q$INSERT INTO voucher_claim (user_id, voucher_id) VALUES (%s, %s)$q$, us2, vch));

    RAISE NOTICE 'ALL DATABASE CHECKS PASSED';
END $$;

ROLLBACK;

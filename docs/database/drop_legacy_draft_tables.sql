-- =====================================================================
-- ONE-TIME CLEANUP — only needed if someone ran the old draft schema.sql /
-- normalized_schema.sql directly in Supabase before Flyway was set up.
--
-- Flyway refuses to start on a non-empty schema without its history table
-- ("Found non-empty schema(s) "public" but no schema history table").
-- This drops exactly the tables/views those drafts created, then Flyway
-- can build the real schema on the next app start.
--
-- SAFETY: it aborts if Flyway has already migrated this database, so it can
-- never wipe the real schema. The draft tables only ever held test rows.
-- Run it in the Supabase SQL editor.
-- =====================================================================

DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        RAISE EXCEPTION 'flyway_schema_history exists — this database is managed by Flyway. Nothing dropped.';
    END IF;
END $$;

DROP VIEW IF EXISTS event_verified;

DROP TABLE IF EXISTS
    voucher_claim, voucher, sponsor,
    user_badge, badge,
    notification, event_follow, event_change_log, event_verification,
    dispute_report, dispute_reason,
    review, itinerary_item, itinerary,
    collection_event, collection,
    attendance, ticket_tier, event_media,
    event, venue, area, event_category,
    users
CASCADE;

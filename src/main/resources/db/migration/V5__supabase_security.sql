-- =====================================================================
-- Ayojon — V5: lock the schema down for Supabase
--
-- Supabase publishes every table in "public" through its auto-generated
-- REST API (PostgREST) to the "anon" and "authenticated" roles. Our users
-- table holds password hashes, and only the Spring Boot backend should
-- talk to the database, so we:
--   1. enable Row Level Security on every table (no policies = API sees
--      nothing; the backend connects as the table owner and is unaffected)
--   2. revoke API-role privileges on everything, now and in the future.
-- On a plain local PostgreSQL the anon/authenticated roles don't exist and
-- step 2 is skipped, so this migration is safe everywhere.
-- =====================================================================

DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables
              WHERE schemaname = current_schema() AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t.tablename);
    END LOOP;
END $$;

DO $$
DECLARE
    r TEXT;
BEGIN
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES    IN SCHEMA %I FROM %I', current_schema(), r);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA %I FROM %I', current_schema(), r);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA %I FROM %I', current_schema(), r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON TABLES    FROM %I', current_schema(), r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON SEQUENCES FROM %I', current_schema(), r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON FUNCTIONS FROM %I', current_schema(), r);
        END IF;
    END LOOP;
END $$;

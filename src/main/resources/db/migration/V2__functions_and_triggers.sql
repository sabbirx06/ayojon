-- =====================================================================
-- Ayojon — V2: functions & triggers (business rules enforced in the DB)
--
-- What the database does automatically, so the Java code doesn't have to:
--   * updated_at columns are refreshed on every UPDATE
--   * role checks: only ORGANIZER/ADMIN create events, only ADMIN moderates
--   * F18 inserting an event_submission_review moves the event's status and
--         notifies the organizer
--   * F7  inserting a VERIFIED event_verification notifies the organizer
--   * F17 every UPDATE of an event writes field-level rows to event_change_log
--         (set event.updated_by in the same UPDATE to record who did it)
--   * F12 followers get a notification when a published event is
--         rescheduled, moves venue or is cancelled
--   * F3  check-ins award points (verified events only), keep users.points
--         in sync and grant milestone badges
--   * F4  voucher claims are validated (window, stock, points threshold)
--   * F9  adding an event to a collection auto-bookmarks it
--   * F10 itinerary items must be published events inside the plan's dates
--   * F14 resolving a dispute stamps resolved_at and notifies the reporter
-- =====================================================================


-- ---------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------

CREATE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END $$;

-- Raises if the user does not exist or does not hold one of the roles.
CREATE FUNCTION assert_user_role(p_user_id BIGINT, p_roles TEXT[], p_action TEXT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_role TEXT;
BEGIN
    SELECT role INTO v_role FROM users WHERE user_id = p_user_id AND is_active;
    IF v_role IS NULL OR NOT v_role = ANY (p_roles) THEN
        RAISE EXCEPTION 'User % (role %) is not allowed to %; required role: %',
            p_user_id, coalesce(v_role, 'none/inactive'), p_action, array_to_string(p_roles, ' or ')
            USING ERRCODE = 'insufficient_privilege';
    END IF;
END $$;

-- F7 an event is verified when its most recent verification decision is VERIFIED.
CREATE FUNCTION event_is_verified(p_event_id BIGINT) RETURNS boolean
LANGUAGE sql STABLE AS $$
    SELECT coalesce((SELECT decision = 'VERIFIED'
                       FROM event_verification
                      WHERE event_id = p_event_id
                      ORDER BY verified_at DESC, verification_id DESC
                      LIMIT 1), FALSE)
$$;

-- F1 great-circle distance in kilometres.
CREATE FUNCTION haversine_km(lat1 NUMERIC, lng1 NUMERIC, lat2 NUMERIC, lng2 NUMERIC) RETURNS double precision
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT 2 * 6371.0088 * asin(sqrt(
               power(sin(radians((lat2 - lat1)::float8) / 2), 2) +
               cos(radians(lat1::float8)) * cos(radians(lat2::float8)) *
               power(sin(radians((lng2 - lng1)::float8) / 2), 2)))
$$;

-- Human-readable local time used in audit rows and notifications.
CREATE FUNCTION fmt_local(ts TIMESTAMPTZ) RETURNS text
LANGUAGE sql STABLE AS $$
    SELECT to_char(ts AT TIME ZONE 'Asia/Dhaka', 'YYYY-MM-DD HH24:MI')
$$;


-- ---------------------------------------------------------------------
-- updated_at
-- ---------------------------------------------------------------------

CREATE TRIGGER trg_users_updated_at      BEFORE UPDATE ON users      FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_event_updated_at      BEFORE UPDATE ON event      FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_collection_updated_at BEFORE UPDATE ON collection FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_itinerary_updated_at  BEFORE UPDATE ON itinerary  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_review_updated_at     BEFORE UPDATE ON review     FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------
-- Events (F16, F17, F12)
-- ---------------------------------------------------------------------

CREATE FUNCTION event_before_write() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.organizer_id IS DISTINCT FROM OLD.organizer_id THEN
        PERFORM assert_user_role(NEW.organizer_id, ARRAY['ORGANIZER', 'ADMIN'], 'own an event');
    END IF;

    IF NEW.status = 'PUBLISHED' AND NEW.published_at IS NULL THEN
        NEW.published_at := now();
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_event_before_write
    BEFORE INSERT OR UPDATE ON event
    FOR EACH ROW EXECUTE FUNCTION event_before_write();

-- F17 field-level audit trail + F12 follower notifications.
CREATE FUNCTION event_after_update() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_by        BIGINT := NEW.updated_by;
    v_changes   TEXT[] := '{}';
    v_old_venue TEXT;
    v_new_venue TEXT;
BEGIN
    IF NEW.start_time IS DISTINCT FROM OLD.start_time THEN
        INSERT INTO event_change_log (event_id, changed_by, change_type, field_name, old_value, new_value)
        VALUES (NEW.event_id, v_by, 'RESCHEDULED', 'start_time', fmt_local(OLD.start_time), fmt_local(NEW.start_time));
        v_changes := v_changes || ('starts ' || fmt_local(NEW.start_time));
    END IF;

    IF NEW.end_time IS DISTINCT FROM OLD.end_time THEN
        INSERT INTO event_change_log (event_id, changed_by, change_type, field_name, old_value, new_value)
        VALUES (NEW.event_id, v_by, 'RESCHEDULED', 'end_time', fmt_local(OLD.end_time), fmt_local(NEW.end_time));
        v_changes := v_changes || ('ends ' || fmt_local(NEW.end_time));
    END IF;

    IF NEW.venue_id IS DISTINCT FROM OLD.venue_id THEN
        SELECT venue_name INTO v_old_venue FROM venue WHERE venue_id = OLD.venue_id;
        SELECT venue_name INTO v_new_venue FROM venue WHERE venue_id = NEW.venue_id;
        INSERT INTO event_change_log (event_id, changed_by, change_type, field_name, old_value, new_value)
        VALUES (NEW.event_id, v_by, 'VENUE_CHANGED', 'venue', v_old_venue, v_new_venue);
        v_changes := v_changes || ('moved to ' || v_new_venue);
    END IF;

    IF NEW.title IS DISTINCT FROM OLD.title THEN
        INSERT INTO event_change_log (event_id, changed_by, change_type, field_name, old_value, new_value)
        VALUES (NEW.event_id, v_by, 'DETAILS_UPDATED', 'title', OLD.title, NEW.title);
    END IF;
    IF NEW.description IS DISTINCT FROM OLD.description THEN
        INSERT INTO event_change_log (event_id, changed_by, change_type, field_name)
        VALUES (NEW.event_id, v_by, 'DETAILS_UPDATED', 'description');
    END IF;
    IF NEW.category_id IS DISTINCT FROM OLD.category_id THEN
        INSERT INTO event_change_log (event_id, changed_by, change_type, field_name, old_value, new_value)
        VALUES (NEW.event_id, v_by, 'DETAILS_UPDATED', 'category',
                (SELECT category_name FROM event_category WHERE category_id = OLD.category_id),
                (SELECT category_name FROM event_category WHERE category_id = NEW.category_id));
    END IF;
    IF NEW.ticket_url IS DISTINCT FROM OLD.ticket_url THEN
        INSERT INTO event_change_log (event_id, changed_by, change_type, field_name, old_value, new_value)
        VALUES (NEW.event_id, v_by, 'DETAILS_UPDATED', 'ticket_url', OLD.ticket_url, NEW.ticket_url);
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO event_change_log (event_id, changed_by, change_type, field_name, old_value, new_value, note)
        VALUES (NEW.event_id, v_by,
                CASE WHEN NEW.status = 'CANCELLED' THEN 'CANCELLED' ELSE 'STATUS_CHANGED' END,
                'status', OLD.status, NEW.status,
                CASE WHEN NEW.status = 'CANCELLED' THEN NEW.cancellation_reason END);
    END IF;

    -- F12: tell followers about changes that affect their plans.
    IF NEW.status = 'CANCELLED' AND OLD.status IS DISTINCT FROM 'CANCELLED' THEN
        INSERT INTO notification (user_id, event_id, notification_type, message)
        SELECT f.user_id, NEW.event_id, 'EVENT_CANCELLED',
               format('"%s" has been cancelled. Reason: %s', NEW.title, NEW.cancellation_reason)
          FROM event_follow f
         WHERE f.event_id = NEW.event_id;
    ELSIF NEW.status = 'PUBLISHED' AND cardinality(v_changes) > 0 THEN
        INSERT INTO notification (user_id, event_id, notification_type, message)
        SELECT f.user_id, NEW.event_id, 'EVENT_UPDATED',
               format('"%s" was updated: %s', NEW.title, array_to_string(v_changes, ', '))
          FROM event_follow f
         WHERE f.event_id = NEW.event_id;
    END IF;

    RETURN NULL;
END $$;

CREATE TRIGGER trg_event_after_update
    AFTER UPDATE ON event
    FOR EACH ROW EXECUTE FUNCTION event_after_update();


-- ---------------------------------------------------------------------
-- F18 admin approval queue
-- ---------------------------------------------------------------------

CREATE FUNCTION submission_review_apply() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_event event%ROWTYPE;
BEGIN
    PERFORM assert_user_role(NEW.admin_id, ARRAY['ADMIN'], 'review event submissions');

    SELECT * INTO v_event FROM event WHERE event_id = NEW.event_id FOR UPDATE;
    IF v_event.status <> 'PENDING_REVIEW' THEN
        RAISE EXCEPTION 'Event % is % — only PENDING_REVIEW submissions can be reviewed',
            NEW.event_id, v_event.status USING ERRCODE = 'check_violation';
    END IF;

    UPDATE event
       SET status     = CASE NEW.decision WHEN 'APPROVED' THEN 'PUBLISHED' ELSE NEW.decision END,
           updated_by = NEW.admin_id
     WHERE event_id = NEW.event_id;

    INSERT INTO notification (user_id, event_id, notification_type, message)
    VALUES (v_event.organizer_id, NEW.event_id, 'SUBMISSION_STATUS',
            CASE NEW.decision
                WHEN 'APPROVED'           THEN format('Your event "%s" was approved and is now live.', v_event.title)
                WHEN 'REJECTED'           THEN format('Your event "%s" was rejected: %s', v_event.title, NEW.comment)
                WHEN 'REVISION_REQUESTED' THEN format('Revisions requested for "%s": %s', v_event.title, NEW.comment)
            END);
    RETURN NEW;
END $$;

CREATE TRIGGER trg_submission_review_apply
    BEFORE INSERT ON event_submission_review
    FOR EACH ROW EXECUTE FUNCTION submission_review_apply();


-- ---------------------------------------------------------------------
-- F7 verified trust badge
-- ---------------------------------------------------------------------

CREATE FUNCTION event_verification_check() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM assert_user_role(NEW.admin_id, ARRAY['ADMIN'], 'verify events');
    IF NEW.decision = 'VERIFIED' AND NEW.source_url IS NULL THEN
        RAISE EXCEPTION 'A VERIFIED decision must cite the official source_url' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_event_verification_check
    BEFORE INSERT ON event_verification
    FOR EACH ROW EXECUTE FUNCTION event_verification_check();

CREATE FUNCTION event_verification_notify() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.decision = 'VERIFIED' THEN
        INSERT INTO notification (user_id, event_id, notification_type, message)
        SELECT e.organizer_id, e.event_id, 'EVENT_VERIFIED',
               format('"%s" received the Verified badge.', e.title)
          FROM event e WHERE e.event_id = NEW.event_id;
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_event_verification_notify
    AFTER INSERT ON event_verification
    FOR EACH ROW EXECUTE FUNCTION event_verification_notify();


-- ---------------------------------------------------------------------
-- F14 dispute reports
-- ---------------------------------------------------------------------

CREATE FUNCTION dispute_report_before_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.resolved_by IS DISTINCT FROM OLD.resolved_by AND NEW.resolved_by IS NOT NULL THEN
        PERFORM assert_user_role(NEW.resolved_by, ARRAY['ADMIN'], 'resolve dispute reports');
    END IF;

    IF NEW.status IN ('RESOLVED', 'DISMISSED') THEN
        NEW.resolved_at := coalesce(NEW.resolved_at, now());
    ELSE
        NEW.resolved_at := NULL;
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO notification (user_id, event_id, notification_type, message)
        VALUES (NEW.reporter_id, NEW.event_id, 'REPORT_UPDATE',
                format('Your report #%s is now %s.%s', NEW.report_id, NEW.status,
                       coalesce(' ' || NEW.resolution_note, '')));
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_dispute_report_before_update
    BEFORE UPDATE ON dispute_report
    FOR EACH ROW EXECUTE FUNCTION dispute_report_before_update();


-- ---------------------------------------------------------------------
-- F3 attendance -> points -> badges
-- ---------------------------------------------------------------------

CREATE FUNCTION attendance_before_insert() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_event event%ROWTYPE;
BEGIN
    SELECT * INTO v_event FROM event WHERE event_id = NEW.event_id;
    IF v_event.status <> 'PUBLISHED' THEN
        RAISE EXCEPTION 'Cannot check in to event % with status %', NEW.event_id, v_event.status
            USING ERRCODE = 'check_violation';
    END IF;

    -- Points are only earned at verified events.
    NEW.points_awarded := CASE WHEN event_is_verified(NEW.event_id) THEN v_event.check_in_points ELSE 0 END;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_attendance_before_insert
    BEFORE INSERT ON attendance
    FOR EACH ROW EXECUTE FUNCTION attendance_before_insert();

-- Recomputes the cached total and grants any newly reached badges.
CREATE FUNCTION refresh_user_points(p_user_id BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_points INT;
BEGIN
    SELECT coalesce(sum(points_awarded), 0) INTO v_points FROM attendance WHERE user_id = p_user_id;
    UPDATE users SET points = v_points WHERE user_id = p_user_id AND points IS DISTINCT FROM v_points;

    WITH new_badges AS (
        INSERT INTO user_badge (user_id, badge_id)
        SELECT p_user_id, b.badge_id FROM badge b WHERE b.milestone_points <= v_points
        ON CONFLICT DO NOTHING
        RETURNING badge_id
    )
    INSERT INTO notification (user_id, notification_type, message)
    SELECT p_user_id, 'BADGE_EARNED', format('You unlocked the "%s" badge!', b.badge_name)
      FROM new_badges nb JOIN badge b USING (badge_id);
END $$;

CREATE FUNCTION attendance_after_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        PERFORM refresh_user_points(NEW.user_id);
    END IF;
    IF TG_OP IN ('DELETE', 'UPDATE') AND (TG_OP = 'DELETE' OR OLD.user_id <> NEW.user_id) THEN
        PERFORM refresh_user_points(OLD.user_id);
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_attendance_after_change
    AFTER INSERT OR UPDATE OR DELETE ON attendance
    FOR EACH ROW EXECUTE FUNCTION attendance_after_change();


-- ---------------------------------------------------------------------
-- F9 collections: fill owner and auto-bookmark
-- ---------------------------------------------------------------------

CREATE FUNCTION collection_item_before_insert() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    SELECT user_id INTO NEW.user_id FROM collection WHERE collection_id = NEW.collection_id;
    IF NEW.user_id IS NULL THEN
        RAISE EXCEPTION 'Collection % does not exist', NEW.collection_id USING ERRCODE = 'foreign_key_violation';
    END IF;

    INSERT INTO bookmark (user_id, event_id) VALUES (NEW.user_id, NEW.event_id)
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_collection_item_before_insert
    BEFORE INSERT ON collection_item
    FOR EACH ROW EXECUTE FUNCTION collection_item_before_insert();


-- ---------------------------------------------------------------------
-- F10 itinerary items must fall inside the plan's dates
-- ---------------------------------------------------------------------

CREATE FUNCTION itinerary_item_check() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_plan  itinerary%ROWTYPE;
    v_event event%ROWTYPE;
BEGIN
    SELECT * INTO v_plan  FROM itinerary WHERE itinerary_id = NEW.itinerary_id;
    SELECT * INTO v_event FROM event     WHERE event_id     = NEW.event_id;

    IF v_event.status <> 'PUBLISHED' THEN
        RAISE EXCEPTION 'Only published events can be scheduled (event % is %)', NEW.event_id, v_event.status
            USING ERRCODE = 'check_violation';
    END IF;

    IF (v_event.start_time AT TIME ZONE 'Asia/Dhaka')::date > v_plan.end_date
       OR (v_event.end_time AT TIME ZONE 'Asia/Dhaka')::date < v_plan.start_date THEN
        RAISE EXCEPTION 'Event % does not take place between % and %', NEW.event_id, v_plan.start_date, v_plan.end_date
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.planned_start IS NOT NULL AND NEW.planned_start < v_event.start_time
       OR NEW.planned_end IS NOT NULL AND NEW.planned_end > v_event.end_time THEN
        RAISE EXCEPTION 'Planned slot must be within the event''s own start and end time'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_itinerary_item_check
    BEFORE INSERT OR UPDATE ON itinerary_item
    FOR EACH ROW EXECUTE FUNCTION itinerary_item_check();


-- ---------------------------------------------------------------------
-- F4 voucher claims
-- ---------------------------------------------------------------------

CREATE FUNCTION voucher_claim_before_insert() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_voucher  voucher%ROWTYPE;
    v_campaign ad_campaign%ROWTYPE;
    v_claimed  INT;
    v_points   INT;
BEGIN
    -- Lock the voucher row so two users can't grab the last unit at once.
    SELECT * INTO v_voucher  FROM voucher     WHERE voucher_id  = NEW.voucher_id FOR UPDATE;
    SELECT * INTO v_campaign FROM ad_campaign WHERE campaign_id = v_voucher.campaign_id;

    IF NOT v_voucher.is_active OR NOT v_campaign.is_active THEN
        RAISE EXCEPTION 'Voucher % is not active', v_voucher.promo_code USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.claimed_at NOT BETWEEN v_voucher.valid_from AND v_voucher.valid_until THEN
        RAISE EXCEPTION 'Voucher % is outside its validity window', v_voucher.promo_code USING ERRCODE = 'check_violation';
    END IF;

    SELECT points INTO v_points FROM users WHERE user_id = NEW.user_id;
    IF v_points < v_voucher.min_points_required THEN
        RAISE EXCEPTION 'Voucher % needs % points (user has %)', v_voucher.promo_code, v_voucher.min_points_required, v_points
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_voucher.total_quantity IS NOT NULL THEN
        SELECT count(*) INTO v_claimed FROM voucher_claim WHERE voucher_id = NEW.voucher_id;
        IF v_claimed >= v_voucher.total_quantity THEN
            RAISE EXCEPTION 'Voucher % is fully claimed', v_voucher.promo_code USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_voucher_claim_before_insert
    BEFORE INSERT ON voucher_claim
    FOR EACH ROW EXECUTE FUNCTION voucher_claim_before_insert();

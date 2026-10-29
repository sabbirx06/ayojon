-- =====================================================================
-- Ayojon — V3: read models for features (views + set-returning functions)
--
-- Call these from Spring Data with native queries, e.g.
--   @Query(value = "SELECT * FROM search_events(:q, :from, :to, :area, :cat, :min, :max)",
--          nativeQuery = true)
-- Every function parameter is optional: pass NULL to skip that filter.
-- =====================================================================


-- ---------------------------------------------------------------------
-- Event read model (F5, F6, F7, F8, F15 — used by every event card)
-- ---------------------------------------------------------------------

-- F7 current verification state per event (latest decision wins).
CREATE VIEW v_event_verification_status AS
SELECT DISTINCT ON (ev.event_id)
       ev.event_id,
       (ev.decision = 'VERIFIED') AS is_verified,
       ev.decision,
       ev.source_url,
       ev.verified_at
  FROM event_verification ev
 ORDER BY ev.event_id, ev.verified_at DESC, ev.verification_id DESC;

-- One row per event with everything an event card / profile page needs.
CREATE VIEW v_event_listing AS
SELECT e.event_id,
       e.title,
       e.description,
       e.status,
       e.start_time,
       e.end_time,
       e.ticket_url,
       e.official_source_url,
       e.cancellation_reason,
       e.check_in_points,
       e.published_at,
       e.created_at,
       e.organizer_id,
       o.name                                   AS organizer_name,
       c.category_id,
       c.category_name,
       v.venue_id,
       v.venue_name,
       v.venue_address,
       v.latitude,
       v.longitude,
       a.area_id,
       a.area_name,
       p.min_price,
       p.max_price,
       coalesce(p.min_price = 0, FALSE)          AS is_free,
       coalesce(vs.is_verified, FALSE)           AS is_verified,
       poster.media_url                         AS poster_url,
       coalesce(t.tags, '{}')                   AS tags,
       r.avg_rating,
       coalesce(r.review_count, 0)              AS review_count,
       coalesce(b.bookmark_count, 0)            AS bookmark_count,
       e.search_vector
  FROM event e
  JOIN users o          ON o.user_id = e.organizer_id
  JOIN event_category c ON c.category_id = e.category_id
  JOIN venue v          ON v.venue_id = e.venue_id
  JOIN area a           ON a.area_id = v.area_id
  LEFT JOIN v_event_verification_status vs ON vs.event_id = e.event_id
  LEFT JOIN event_media poster ON poster.event_id = e.event_id AND poster.media_type = 'POSTER'
  LEFT JOIN LATERAL (SELECT min(price) AS min_price, max(price) AS max_price
                       FROM ticket_tier tt WHERE tt.event_id = e.event_id) p ON TRUE
  LEFT JOIN LATERAL (SELECT array_agg(tg.tag_name ORDER BY tg.tag_name)::text[] AS tags
                       FROM event_tag et JOIN tag tg USING (tag_id)
                      WHERE et.event_id = e.event_id) t ON TRUE
  LEFT JOIN LATERAL (SELECT round(avg(rating), 2) AS avg_rating, count(*) AS review_count
                       FROM review rv WHERE rv.event_id = e.event_id) r ON TRUE
  LEFT JOIN LATERAL (SELECT count(*) AS bookmark_count
                       FROM bookmark bm WHERE bm.event_id = e.event_id) b ON TRUE;


-- ---------------------------------------------------------------------
-- F5 multi-parameter search
-- ---------------------------------------------------------------------

-- Keyword uses web-search syntax ("jazz -rock", "\"food festival\"") over
-- title/description, and also matches venue / area / category / tag names.
-- Price filters use the event's cheapest tier. Only PUBLISHED events that
-- have not ended are returned; best keyword match first, then soonest.
CREATE FUNCTION search_events(
    p_keyword     TEXT        DEFAULT NULL,
    p_from        TIMESTAMPTZ DEFAULT NULL,
    p_to          TIMESTAMPTZ DEFAULT NULL,
    p_area_id     BIGINT      DEFAULT NULL,
    p_category_id BIGINT      DEFAULT NULL,
    p_min_price   NUMERIC     DEFAULT NULL,
    p_max_price   NUMERIC     DEFAULT NULL
) RETURNS SETOF v_event_listing
LANGUAGE sql STABLE AS $$
    WITH q AS (
        SELECT nullif(btrim(p_keyword), '')                                          AS kw,
               websearch_to_tsquery('english', coalesce(nullif(btrim(p_keyword), ''), '')) AS tsq
    )
    SELECT l.*
      FROM v_event_listing l, q
     WHERE l.status = 'PUBLISHED'
       AND l.end_time >= coalesce(p_from, now())
       AND (p_to          IS NULL OR l.start_time <= p_to)
       AND (p_area_id     IS NULL OR l.area_id = p_area_id)
       AND (p_category_id IS NULL OR l.category_id = p_category_id)
       AND (p_min_price   IS NULL OR l.min_price >= p_min_price)
       AND (p_max_price   IS NULL OR l.min_price <= p_max_price)
       AND (q.kw IS NULL
            OR l.search_vector @@ q.tsq
            OR l.venue_name    ILIKE '%' || q.kw || '%'
            OR l.area_name     ILIKE '%' || q.kw || '%'
            OR l.category_name ILIKE '%' || q.kw || '%'
            OR EXISTS (SELECT 1 FROM unnest(l.tags) AS tg (name) WHERE tg.name ILIKE '%' || q.kw || '%'))
     ORDER BY CASE WHEN q.kw IS NULL THEN 0 ELSE ts_rank(l.search_vector, q.tsq) END DESC,
              l.start_time
$$;


-- ---------------------------------------------------------------------
-- F1 nearby discovery
-- ---------------------------------------------------------------------

-- Upcoming published events within p_radius_km of the user's GPS point,
-- nearest first. A lat/lng bounding box pre-filter keeps it index-friendly.
CREATE FUNCTION events_nearby(p_lat NUMERIC, p_lng NUMERIC, p_radius_km NUMERIC DEFAULT 5)
RETURNS TABLE (event_id BIGINT, title VARCHAR, start_time TIMESTAMPTZ, end_time TIMESTAMPTZ,
               venue_name VARCHAR, area_name VARCHAR, latitude NUMERIC, longitude NUMERIC,
               category_name VARCHAR, min_price NUMERIC, is_verified BOOLEAN, poster_url TEXT,
               distance_km DOUBLE PRECISION)
LANGUAGE sql STABLE AS $$
    SELECT l.event_id, l.title, l.start_time, l.end_time, l.venue_name, l.area_name,
           l.latitude, l.longitude, l.category_name, l.min_price, l.is_verified, l.poster_url,
           round(haversine_km(p_lat, p_lng, l.latitude, l.longitude)::numeric, 2)::double precision
      FROM v_event_listing l
     WHERE l.status = 'PUBLISHED'
       AND l.end_time >= now()
       AND l.latitude  BETWEEN p_lat - p_radius_km / 111.0 AND p_lat + p_radius_km / 111.0
       AND l.longitude BETWEEN p_lng - p_radius_km / (111.0 * cos(radians(p_lat::float8)))::numeric
                           AND p_lng + p_radius_km / (111.0 * cos(radians(p_lat::float8)))::numeric
       AND haversine_km(p_lat, p_lng, l.latitude, l.longitude) <= p_radius_km
     ORDER BY 13
$$;


-- ---------------------------------------------------------------------
-- F8 trending (weekly bookmark frequency)
-- ---------------------------------------------------------------------

CREATE VIEW v_trending_events AS
SELECT dense_rank() OVER (ORDER BY w.weekly_bookmarks DESC) AS trend_rank,
       w.weekly_bookmarks,
       l.*
  FROM v_event_listing l
  JOIN LATERAL (SELECT count(*) AS weekly_bookmarks
                  FROM bookmark b
                 WHERE b.event_id = l.event_id
                   AND b.bookmarked_at >= now() - INTERVAL '7 days') w ON TRUE
 WHERE l.status = 'PUBLISHED'
   AND l.end_time >= now()
   AND w.weekly_bookmarks > 0;


-- ---------------------------------------------------------------------
-- F3 leaderboard
-- ---------------------------------------------------------------------

CREATE VIEW v_leaderboard AS
SELECT rank() OVER (ORDER BY u.points DESC)  AS leaderboard_rank,
       u.user_id,
       u.name,
       u.avatar_url,
       u.points,
       (SELECT count(*) FROM attendance a WHERE a.user_id = u.user_id) AS events_attended,
       (SELECT count(*) FROM user_badge ub WHERE ub.user_id = u.user_id) AS badge_count
  FROM users u
 WHERE u.is_active AND u.role = 'USER';


-- ---------------------------------------------------------------------
-- F2 heat map & F20 attendance dashboard
-- ---------------------------------------------------------------------

-- One weighted point per venue a user has checked in at.
CREATE VIEW v_user_heatmap AS
SELECT a.user_id,
       v.venue_id,
       v.venue_name,
       ar.area_name,
       v.latitude,
       v.longitude,
       count(*)              AS visit_count,
       max(a.checked_in_at)  AS last_visit
  FROM attendance a
  JOIN event e  ON e.event_id = a.event_id
  JOIN venue v  ON v.venue_id = e.venue_id
  JOIN area ar  ON ar.area_id = v.area_id
 GROUP BY a.user_id, v.venue_id, v.venue_name, ar.area_name, v.latitude, v.longitude;

CREATE VIEW v_user_category_stats AS
SELECT a.user_id, c.category_id, c.category_name,
       count(*)             AS events_attended,
       max(a.checked_in_at) AS last_check_in
  FROM attendance a
  JOIN event e          ON e.event_id = a.event_id
  JOIN event_category c ON c.category_id = e.category_id
 GROUP BY a.user_id, c.category_id, c.category_name;

CREATE VIEW v_user_area_stats AS
SELECT a.user_id, ar.area_id, ar.area_name,
       count(*)             AS events_attended,
       max(a.checked_in_at) AS last_check_in
  FROM attendance a
  JOIN event e ON e.event_id = a.event_id
  JOIN venue v ON v.venue_id = e.venue_id
  JOIN area ar ON ar.area_id = v.area_id
 GROUP BY a.user_id, ar.area_id, ar.area_name;

-- Favourite category / most visited area: highest count, ties go to the
-- most recently attended.
CREATE VIEW v_user_attendance_summary AS
SELECT u.user_id,
       u.name,
       u.points,
       count(a.attendance_id)         AS total_events_attended,
       min(a.checked_in_at)           AS first_check_in,
       max(a.checked_in_at)           AS last_check_in,
       (SELECT cs.category_name FROM v_user_category_stats cs WHERE cs.user_id = u.user_id
         ORDER BY cs.events_attended DESC, cs.last_check_in DESC LIMIT 1) AS favorite_category,
       (SELECT ars.area_name FROM v_user_area_stats ars WHERE ars.user_id = u.user_id
         ORDER BY ars.events_attended DESC, ars.last_check_in DESC LIMIT 1) AS most_visited_area,
       (SELECT count(*) FROM user_badge ub WHERE ub.user_id = u.user_id) AS badges_earned
  FROM users u
  LEFT JOIN attendance a ON a.user_id = u.user_id
 GROUP BY u.user_id, u.name, u.points;

CREATE VIEW v_user_monthly_attendance AS
SELECT a.user_id,
       date_trunc('month', a.checked_in_at AT TIME ZONE 'Asia/Dhaka')::date AS month,
       count(*)              AS events_attended,
       sum(a.points_awarded) AS points_earned
  FROM attendance a
 GROUP BY 1, 2;


-- ---------------------------------------------------------------------
-- F19 recommendations from bookmarks
-- ---------------------------------------------------------------------

-- Scores upcoming events by how often the user bookmarked the same
-- category (x3), the same tags (x2) and the same area (x1), plus a small
-- weekly-popularity boost. Users with no bookmarks get popular events.
CREATE FUNCTION recommend_events(p_user_id BIGINT, p_limit INT DEFAULT 10)
RETURNS TABLE (event_id BIGINT, title VARCHAR, start_time TIMESTAMPTZ, category_name VARCHAR,
               area_name VARCHAR, min_price NUMERIC, is_verified BOOLEAN, poster_url TEXT,
               score NUMERIC)
LANGUAGE sql STABLE AS $$
    WITH saved AS (
        SELECT e.event_id, e.category_id, v.area_id
          FROM bookmark b
          JOIN event e ON e.event_id = b.event_id
          JOIN venue v ON v.venue_id = e.venue_id
         WHERE b.user_id = p_user_id
    ),
    cat_pref  AS (SELECT category_id, count(*) AS w FROM saved GROUP BY category_id),
    area_pref AS (SELECT area_id,     count(*) AS w FROM saved GROUP BY area_id),
    tag_pref  AS (SELECT et.tag_id,   count(*) AS w FROM saved s JOIN event_tag et USING (event_id) GROUP BY et.tag_id),
    candidates AS (
        SELECT l.*,
               3 * coalesce((SELECT w FROM cat_pref  cp WHERE cp.category_id = l.category_id), 0)
             + 1 * coalesce((SELECT w FROM area_pref ap WHERE ap.area_id = l.area_id), 0)
             + 2 * coalesce((SELECT sum(tp.w) FROM event_tag et JOIN tag_pref tp USING (tag_id)
                              WHERE et.event_id = l.event_id), 0)
             + 0.1 * (SELECT count(*) FROM bookmark b
                       WHERE b.event_id = l.event_id AND b.bookmarked_at >= now() - INTERVAL '7 days')
               AS score
          FROM v_event_listing l
         WHERE l.status = 'PUBLISHED'
           AND l.start_time >= now()
           AND NOT EXISTS (SELECT 1 FROM bookmark   b WHERE b.user_id = p_user_id AND b.event_id = l.event_id)
           AND NOT EXISTS (SELECT 1 FROM attendance a WHERE a.user_id = p_user_id AND a.event_id = l.event_id)
    )
    SELECT event_id, title, start_time, category_name, area_name, min_price, is_verified, poster_url,
           round(score::numeric, 2)
      FROM candidates
     ORDER BY score DESC, is_verified DESC, start_time
     LIMIT p_limit
$$;


-- ---------------------------------------------------------------------
-- F11 overlap detection
-- ---------------------------------------------------------------------

-- Live (pending or published) events whose time ranges overlap at the same
-- venue — shown to admins in the approval queue.
CREATE VIEW v_venue_event_overlap AS
SELECT e1.venue_id,
       e1.event_id   AS event_id,
       e1.title      AS event_title,
       e2.event_id   AS overlapping_event_id,
       e2.title      AS overlapping_event_title,
       greatest(e1.start_time, e2.start_time) AS overlap_start,
       least(e1.end_time, e2.end_time)        AS overlap_end
  FROM event e1
  JOIN event e2 ON e2.venue_id = e1.venue_id
               AND e2.event_id <> e1.event_id
               AND e1.start_time < e2.end_time
               AND e2.start_time < e1.end_time
 WHERE e1.status IN ('PENDING_REVIEW', 'PUBLISHED')
   AND e2.status IN ('PENDING_REVIEW', 'PUBLISHED');

-- Check a proposed time slot before saving (organizer form). Pass the
-- event's own id as p_exclude_event_id when editing. NULL venue = any venue.
CREATE FUNCTION find_overlapping_events(p_start TIMESTAMPTZ, p_end TIMESTAMPTZ,
                                        p_venue_id BIGINT DEFAULT NULL,
                                        p_exclude_event_id BIGINT DEFAULT NULL)
RETURNS TABLE (event_id BIGINT, title VARCHAR, venue_id BIGINT, start_time TIMESTAMPTZ, end_time TIMESTAMPTZ)
LANGUAGE sql STABLE AS $$
    SELECT e.event_id, e.title, e.venue_id, e.start_time, e.end_time
      FROM event e
     WHERE e.status IN ('PENDING_REVIEW', 'PUBLISHED')
       AND e.start_time < p_end
       AND p_start < e.end_time
       AND (p_venue_id IS NULL OR e.venue_id = p_venue_id)
       AND (p_exclude_event_id IS NULL OR e.event_id <> p_exclude_event_id)
     ORDER BY e.start_time
$$;


-- ---------------------------------------------------------------------
-- F10 itinerary, F11 schedule conflicts, F13 calendar export
-- ---------------------------------------------------------------------

-- Chronological schedule with effective slot times and a conflict flag.
CREATE VIEW v_itinerary_schedule AS
WITH slots AS (
    SELECT ii.itinerary_id,
           it.user_id,
           ii.event_id,
           e.title,
           e.status,
           v.venue_name,
           v.venue_address,
           v.latitude,
           v.longitude,
           coalesce(ii.planned_start, e.start_time) AS slot_start,
           coalesce(ii.planned_end,   e.end_time)   AS slot_end,
           ii.note
      FROM itinerary_item ii
      JOIN itinerary it ON it.itinerary_id = ii.itinerary_id
      JOIN event e      ON e.event_id = ii.event_id
      JOIN venue v      ON v.venue_id = e.venue_id
)
SELECT s.*,
       row_number() OVER (PARTITION BY s.itinerary_id ORDER BY s.slot_start, s.event_id) AS sequence_no,
       EXISTS (SELECT 1 FROM slots o
                WHERE o.itinerary_id = s.itinerary_id
                  AND o.event_id <> s.event_id
                  AND o.slot_start < s.slot_end
                  AND s.slot_start < o.slot_end) AS has_conflict
  FROM slots s;

-- Each overlapping pair once (event_id < other_event_id).
CREATE VIEW v_itinerary_conflict AS
SELECT a.itinerary_id,
       a.user_id,
       a.event_id,
       a.title       AS event_title,
       b.event_id    AS other_event_id,
       b.title       AS other_event_title,
       greatest(a.slot_start, b.slot_start) AS overlap_start,
       least(a.slot_end, b.slot_end)        AS overlap_end
  FROM v_itinerary_schedule a
  JOIN v_itinerary_schedule b
    ON b.itinerary_id = a.itinerary_id
   AND a.event_id < b.event_id
   AND a.slot_start < b.slot_end
   AND b.slot_start < a.slot_end;

-- Ready-made VEVENT fields for .ics / CSV export (times are UTC).
CREATE VIEW v_calendar_export AS
SELECT s.itinerary_id,
       s.user_id,
       s.event_id,
       'ayojon-event-' || s.event_id || '@ayojon'               AS uid,
       s.title                                                   AS summary,
       e.description,
       s.slot_start                                              AS dtstart,
       s.slot_end                                                AS dtend,
       s.venue_name || ', ' || s.venue_address                   AS location,
       s.latitude,
       s.longitude,
       coalesce(e.ticket_url, e.official_source_url)             AS url,
       CASE WHEN e.status = 'CANCELLED' THEN 'CANCELLED' ELSE 'CONFIRMED' END AS ical_status,
       e.updated_at                                              AS last_modified
  FROM v_itinerary_schedule s
  JOIN event e ON e.event_id = s.event_id;


-- ---------------------------------------------------------------------
-- F17 public audit trail, F18 admin queue & organizer status
-- ---------------------------------------------------------------------

CREATE VIEW v_public_event_change_log AS
SELECT cl.change_id,
       cl.event_id,
       e.title AS event_title,
       cl.change_type,
       cl.field_name,
       cl.old_value,
       cl.new_value,
       cl.note,
       u.name  AS changed_by_name,
       cl.changed_at
  FROM event_change_log cl
  JOIN event e      ON e.event_id = cl.event_id
  LEFT JOIN users u ON u.user_id = cl.changed_by
 WHERE e.status IN ('PUBLISHED', 'CANCELLED')
   AND cl.changed_at >= e.published_at;          -- pre-publication edits are private

CREATE VIEW v_admin_submission_queue AS
SELECT e.event_id,
       e.title,
       e.organizer_id,
       o.name AS organizer_name,
       e.start_time,
       e.end_time,
       v.venue_name,
       e.created_at,
       e.updated_at AS last_updated,
       (SELECT count(*) FROM event_submission_review r WHERE r.event_id = e.event_id) AS previous_reviews,
       (SELECT count(*) FROM v_venue_event_overlap ov WHERE ov.event_id = e.event_id) AS venue_overlaps,
       e.ticket_url,
       e.official_source_url
  FROM event e
  JOIN users o ON o.user_id = e.organizer_id
  JOIN venue v ON v.venue_id = e.venue_id
 WHERE e.status = 'PENDING_REVIEW';

CREATE VIEW v_organizer_submission_status AS
SELECT e.event_id,
       e.organizer_id,
       e.title,
       e.status,
       e.created_at AS submitted_at,
       e.published_at,
       coalesce(vs.is_verified, FALSE) AS is_verified,
       lr.decision     AS last_decision,
       lr.comment      AS last_comment,
       lr.reviewed_at  AS last_reviewed_at
  FROM event e
  LEFT JOIN v_event_verification_status vs ON vs.event_id = e.event_id
  LEFT JOIN LATERAL (SELECT r.decision, r.comment, r.reviewed_at
                       FROM event_submission_review r
                      WHERE r.event_id = e.event_id
                      ORDER BY r.reviewed_at DESC, r.review_id DESC
                      LIMIT 1) lr ON TRUE;


-- ---------------------------------------------------------------------
-- F14 dispute queue, F4 voucher availability
-- ---------------------------------------------------------------------

CREATE VIEW v_dispute_queue AS
SELECT d.report_id,
       d.status,
       dr.reason_code,
       dr.reason_name,
       d.details,
       d.evidence_url,
       d.event_id,
       e.title AS event_title,
       d.reporter_id,
       u.name  AS reporter_name,
       d.created_at,
       (SELECT count(*) FROM dispute_report d2
         WHERE d2.event_id = d.event_id AND d2.status IN ('PENDING', 'UNDER_REVIEW')) AS open_reports_for_event
  FROM dispute_report d
  JOIN dispute_reason dr ON dr.reason_id = d.reason_id
  JOIN event e           ON e.event_id = d.event_id
  JOIN users u           ON u.user_id = d.reporter_id
 WHERE d.status IN ('PENDING', 'UNDER_REVIEW');

CREATE VIEW v_voucher_availability AS
SELECT vo.voucher_id,
       vo.promo_code,
       vo.description,
       vo.discount_percent,
       vo.max_discount_amount,
       vo.min_points_required,
       vo.valid_from,
       vo.valid_until,
       vo.booking_url,
       vo.event_id,
       c.campaign_id,
       c.campaign_name,
       s.sponsor_id,
       s.sponsor_name,
       s.logo_url,
       vo.total_quantity,
       cl.claimed,
       CASE WHEN vo.total_quantity IS NULL THEN NULL ELSE vo.total_quantity - cl.claimed END AS remaining,
       (vo.is_active AND c.is_active
        AND now() BETWEEN vo.valid_from AND vo.valid_until
        AND (vo.total_quantity IS NULL OR cl.claimed < vo.total_quantity)) AS is_claimable
  FROM voucher vo
  JOIN ad_campaign c ON c.campaign_id = vo.campaign_id
  JOIN sponsor s     ON s.sponsor_id = c.sponsor_id
  JOIN LATERAL (SELECT count(*) AS claimed FROM voucher_claim vc WHERE vc.voucher_id = vo.voucher_id) cl ON TRUE;

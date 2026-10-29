 
-- 1. Users
CREATE TABLE IF NOT EXISTS users (
    user_id       SERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50) NOT NULL DEFAULT 'USER'
                  CHECK (role IN ('USER', 'ORGANIZER', 'ADMIN')),
    points        INT NOT NULL DEFAULT 0 CHECK (points >= 0),  -- cached total (deliberate denormalization)
    created_at    TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
 
-- 2. Event categories
CREATE TABLE IF NOT EXISTS event_category (
    category_id   SERIAL PRIMARY KEY,
    category_name VARCHAR(100) UNIQUE NOT NULL
);
 
-- 3. Areas (NEW: extracted from event.area)
CREATE TABLE IF NOT EXISTS area (
    area_id   SERIAL PRIMARY KEY,
    area_name VARCHAR(100) UNIQUE NOT NULL
);
 
-- 4. Venues (NEW: extracted from event; removes transitive dependencies)
CREATE TABLE IF NOT EXISTS venue (
    venue_id      SERIAL PRIMARY KEY,
    area_id       INT NOT NULL REFERENCES area(area_id) ON DELETE RESTRICT,
    venue_name    VARCHAR(255) NOT NULL,
    venue_address TEXT NOT NULL,
    latitude      DECIMAL(10, 7) NOT NULL,
    longitude     DECIMAL(10, 7) NOT NULL,
    UNIQUE (venue_name, venue_address)
);
 
-- 5. Events
CREATE TABLE IF NOT EXISTS event (
    event_id     SERIAL PRIMARY KEY,
    organizer_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    category_id  INT NOT NULL REFERENCES event_category(category_id) ON DELETE RESTRICT,
    venue_id     INT NOT NULL REFERENCES venue(venue_id) ON DELETE RESTRICT,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    start_time   TIMESTAMPTZ NOT NULL,
    end_time     TIMESTAMPTZ NOT NULL,
    ticket_url   TEXT,
    status       VARCHAR(50) NOT NULL DEFAULT 'PUBLISHED'
                 CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    created_at   TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CHECK (end_time > start_time)
    -- removed: area, venue_*, latitude, longitude  -> venue / area
    -- removed: base_price   -> derivable from ticket_tier (free event = tier priced 0)
    -- removed: poster_url   -> event_media with media_type 'POSTER'
    -- removed: is_verified  -> derived from event_verification (see view below)
);
 
-- 6. Event media
CREATE TABLE IF NOT EXISTS event_media (
    media_id   SERIAL PRIMARY KEY,
    event_id   INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    media_url  TEXT NOT NULL,
    media_type VARCHAR(50) NOT NULL DEFAULT 'IMAGE'
               CHECK (media_type IN ('IMAGE', 'VIDEO', 'POSTER'))
);
 
-- 7. Ticket tiers
CREATE TABLE IF NOT EXISTS ticket_tier (
    tier_id   SERIAL PRIMARY KEY,
    event_id  INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    tier_name VARCHAR(100) NOT NULL,
    price     DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    UNIQUE (event_id, tier_name)
);
 
-- 8. Attendance
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id SERIAL PRIMARY KEY,
    user_id       INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id      INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    checked_in_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, event_id)
);
 
-- 9. Collections
CREATE TABLE IF NOT EXISTS collection (
    collection_id   SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    collection_name VARCHAR(150) NOT NULL,
    is_private      BOOLEAN NOT NULL DEFAULT FALSE
);
 
-- 10. Collection items
CREATE TABLE IF NOT EXISTS collection_event (
    collection_id INT NOT NULL REFERENCES collection(collection_id) ON DELETE CASCADE,
    event_id      INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    saved_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, event_id)
);
 
-- 11. Itineraries
CREATE TABLE IF NOT EXISTS itinerary (
    itinerary_id SERIAL PRIMARY KEY,
    user_id      INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    plan_name    VARCHAR(150) NOT NULL,
    plan_date    DATE NOT NULL
);
 
-- 12. Itinerary items
CREATE TABLE IF NOT EXISTS itinerary_item (
    itinerary_id    INT NOT NULL REFERENCES itinerary(itinerary_id) ON DELETE CASCADE,
    event_id        INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    scheduled_start TIMESTAMPTZ,
    scheduled_end   TIMESTAMPTZ,
    PRIMARY KEY (itinerary_id, event_id),
    CHECK (scheduled_end IS NULL OR scheduled_start IS NULL OR scheduled_end > scheduled_start)
    -- removed: sequence_no -> order is derivable from scheduled_start
);
 
-- 13. Reviews
CREATE TABLE IF NOT EXISTS review (
    review_id   SERIAL PRIMARY KEY,
    user_id     INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id    INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    rating      INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text TEXT,
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, event_id)
);
 
-- 14. Dispute reasons (NEW: lookup replacing free-text reason_category)
CREATE TABLE IF NOT EXISTS dispute_reason (
    reason_id   SERIAL PRIMARY KEY,
    reason_name VARCHAR(100) UNIQUE NOT NULL
);
 
-- 15. Dispute reports
CREATE TABLE IF NOT EXISTS dispute_report (
    report_id   SERIAL PRIMARY KEY,
    reporter_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id    INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    reason_id   INT NOT NULL REFERENCES dispute_reason(reason_id) ON DELETE RESTRICT,
    details     TEXT,
    status      VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED')),
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
 
-- 16. Event verification log
CREATE TABLE IF NOT EXISTS event_verification (
    verification_id SERIAL PRIMARY KEY,
    event_id        INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    admin_id        INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    source_url      TEXT,
    decision        VARCHAR(50) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    verified_at     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
 
-- Replaces event.is_verified: latest decision per event
CREATE OR REPLACE VIEW event_verified AS
SELECT DISTINCT ON (event_id)
       event_id,
       (decision = 'APPROVED') AS is_verified
FROM event_verification
ORDER BY event_id, verified_at DESC;
 
-- 17. Event change log
CREATE TABLE IF NOT EXISTS event_change_log (
    change_id          SERIAL PRIMARY KEY,
    event_id           INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    changed_by         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    change_description TEXT NOT NULL,
    changed_at         TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
 
-- 18. Event follows
CREATE TABLE IF NOT EXISTS event_follow (
    user_id     INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id    INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    followed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, event_id)
);
 
-- 19. Notifications
CREATE TABLE IF NOT EXISTS notification (
    notification_id SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id        INT REFERENCES event(event_id) ON DELETE SET NULL,
    message         TEXT NOT NULL,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
 
-- 20. Badges
CREATE TABLE IF NOT EXISTS badge (
    badge_id         SERIAL PRIMARY KEY,
    badge_name       VARCHAR(100) UNIQUE NOT NULL,
    milestone_points INT NOT NULL CHECK (milestone_points > 0),
    description      TEXT
);
 
-- 21. User badges
CREATE TABLE IF NOT EXISTS user_badge (
    user_id       INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    badge_id      INT NOT NULL REFERENCES badge(badge_id) ON DELETE CASCADE,
    attendance_id INT REFERENCES attendance(attendance_id) ON DELETE SET NULL,
    earned_at     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, badge_id)
);
 
-- 22. Sponsors
CREATE TABLE IF NOT EXISTS sponsor (
    sponsor_id   SERIAL PRIMARY KEY,
    sponsor_name VARCHAR(150) NOT NULL
);
 
-- 23. Vouchers
CREATE TABLE IF NOT EXISTS voucher (
    voucher_id       SERIAL PRIMARY KEY,
    sponsor_id       INT NOT NULL REFERENCES sponsor(sponsor_id) ON DELETE CASCADE,
    promo_code       VARCHAR(50) UNIQUE NOT NULL,
    discount_percent DECIMAL(5, 2) NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
    valid_until      TIMESTAMPTZ NOT NULL
);
 
-- 24. Voucher claims
CREATE TABLE IF NOT EXISTS voucher_claim (
    user_id    INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    voucher_id INT NOT NULL REFERENCES voucher(voucher_id) ON DELETE CASCADE,
    claimed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, voucher_id)
);
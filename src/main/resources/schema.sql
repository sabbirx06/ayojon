-- 1. Users table 
CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'USER', -- USER, ORGANIZER, ADMIN
    points INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Event Categories
CREATE TABLE IF NOT EXISTS event_category (
    category_id SERIAL PRIMARY KEY,
    category_name VARCHAR(100) UNIQUE NOT NULL
);

-- 3. Events
CREATE TABLE IF NOT EXISTS event (
    event_id SERIAL PRIMARY KEY,
    organizer_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    category_id INT NOT NULL REFERENCES event_category(category_id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    area VARCHAR(100) NOT NULL, 
    venue_name VARCHAR(255) NOT NULL,
    venue_address TEXT NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    base_price DECIMAL(10, 2) DEFAULT 0.00,
    poster_url TEXT,
    ticket_url TEXT,
    status VARCHAR(50) DEFAULT 'PUBLISHED', 
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Event Media
CREATE TABLE IF NOT EXISTS event_media (
    media_id SERIAL PRIMARY KEY,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    media_url TEXT NOT NULL,
    media_type VARCHAR(50) DEFAULT 'IMAGE' 
);

-- 5. Ticket Tiers
CREATE TABLE IF NOT EXISTS ticket_tier (
    tier_id SERIAL PRIMARY KEY,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    tier_name VARCHAR(100) NOT NULL, 
    price DECIMAL(10, 2) NOT NULL
);

-- 6. Attendance & Check-ins
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    checked_in_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, event_id)
);

-- 7. Collections 
CREATE TABLE IF NOT EXISTS collection (
    collection_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    collection_name VARCHAR(150) NOT NULL,
    is_private BOOLEAN DEFAULT FALSE
);

-- 8. Collection Items 
CREATE TABLE IF NOT EXISTS collection_event (
    collection_id INT NOT NULL REFERENCES collection(collection_id) ON DELETE CASCADE,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, event_id)
);

-- 9. Itineraries
CREATE TABLE IF NOT EXISTS itinerary (
    itinerary_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    plan_name VARCHAR(150) NOT NULL,
    plan_date DATE NOT NULL
);

-- 10. Itinerary Items 
CREATE TABLE IF NOT EXISTS itinerary_item (
    itinerary_id INT NOT NULL REFERENCES itinerary(itinerary_id) ON DELETE CASCADE,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    scheduled_start TIMESTAMP,
    scheduled_end TIMESTAMP,
    sequence_no INT DEFAULT 1,
    PRIMARY KEY (itinerary_id, event_id)
);

-- 11. Reviews & Ratings
CREATE TABLE IF NOT EXISTS review (
    review_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    rating INT CHECK (rating >= 1 AND rating <= 5),
    review_text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, event_id)
);

-- 12. Dispute Reports
CREATE TABLE IF NOT EXISTS dispute_report (
    report_id SERIAL PRIMARY KEY,
    reporter_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    reason_category VARCHAR(100) NOT NULL,
    details TEXT,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 13. Event Verification Log
CREATE TABLE IF NOT EXISTS event_verification (
    verification_id SERIAL PRIMARY KEY,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    admin_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    source_url TEXT,
    decision VARCHAR(50) NOT NULL, 
    verified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14. Event Change Log
CREATE TABLE IF NOT EXISTS event_change_log (
    change_id SERIAL PRIMARY KEY,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    changed_by INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    change_description TEXT NOT NULL,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 15. Event Follows
CREATE TABLE IF NOT EXISTS event_follow (
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id INT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    followed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, event_id)
);

-- 16. Notifications
CREATE TABLE IF NOT EXISTS notification (
    notification_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    event_id INT REFERENCES event(event_id) ON DELETE SET NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 17. Badges
CREATE TABLE IF NOT EXISTS badge (
    badge_id SERIAL PRIMARY KEY,
    badge_name VARCHAR(100) UNIQUE NOT NULL,
    milestone_points INT NOT NULL,
    description TEXT
);

-- 18. User Badges Earned
CREATE TABLE IF NOT EXISTS user_badge (
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    badge_id INT NOT NULL REFERENCES badge(badge_id) ON DELETE CASCADE,
    attendance_id INT REFERENCES attendance(attendance_id) ON DELETE SET NULL,
    earned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, badge_id)
);

-- 19. Sponsors
CREATE TABLE IF NOT EXISTS sponsor (
    sponsor_id SERIAL PRIMARY KEY,
    sponsor_name VARCHAR(150) NOT NULL
);

-- 20. Vouchers
CREATE TABLE IF NOT EXISTS voucher (
    voucher_id SERIAL PRIMARY KEY,
    sponsor_id INT NOT NULL REFERENCES sponsor(sponsor_id) ON DELETE CASCADE,
    promo_code VARCHAR(50) UNIQUE NOT NULL,
    discount_percent DECIMAL(5, 2) NOT NULL,
    valid_until TIMESTAMP NOT NULL
);

-- 21. Voucher Claims
CREATE TABLE IF NOT EXISTS voucher_claim (
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    voucher_id INT NOT NULL REFERENCES voucher(voucher_id) ON DELETE CASCADE,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, voucher_id)
);

-- =====================================================================
-- Ayojon — demo data (Flyway repeatable migration)
--
-- Realistic Dhaka events so every screen has data on day one. Dates are
-- relative to the moment the script runs: some events are in the past
-- (check-ins, reviews, dashboard, heat map), some are this coming weekend
-- (itinerary), the rest are upcoming (search, trending, recommendations).
--
-- Every row goes through the real triggers, so this script doubles as an
-- end-to-end check of the business rules (approvals publish events,
-- check-ins award points and badges, edits write the audit log and
-- notify followers, ...).
--
-- It only runs once per database: if the demo admin already exists it does
-- nothing. Disable it in production by removing classpath:db/seed from
-- spring.flyway.locations.
--
-- All demo accounts use the password:  password123   (BCrypt, cost 10)
-- =====================================================================

DO $$
DECLARE
    pw CONSTANT TEXT := '$2a$10$7mY1xG97/P8xGhPgmk0DU.bCIRO0w9J4pscTSt7a6xnF1Vi9ZGSki';
    today DATE := (now() AT TIME ZONE 'Asia/Dhaka')::date;
    fri   DATE;      -- next Friday (start of the Bangladeshi weekend)
    sat   DATE;

    admin BIGINT; org_beats BIGINT; org_tech BIGINT; org_food BIGINT;
    rafi BIGINT; nabila BIGINT; tanvir BIGINT; sadia BIGINT; arif BIGINT;

    v_hatir BIGINT; v_bicc BIGINT; v_lalbagh BIGINT; v_tsc BIGINT; v_sarobar BIGINT;
    v_shilpalaya BIGINT; v_gulshan BIGINT; v_iccb BIGINT; v_shilpakala BIGINT;
    v_mirpur BIGINT; v_uttara BIGINT; v_banani BIGINT;

    e_acoustic BIGINT; e_ai BIGINT; e_foodwalk BIGINT; e_openmic BIGINT;
    e_carnival BIGINT; e_classical BIGINT; e_run BIGINT; e_photo BIGINT;
    e_pitch BIGINT; e_hack BIGINT; e_folk BIGINT; e_cricket BIGINT;
    e_career BIGINT; e_kids BIGINT; e_comedy BIGINT; e_scam BIGINT;

    c_rafi_weekend BIGINT; c_rafi_wish BIGINT; c_nabila BIGINT;
    it_rafi BIGINT;
    camp_tickets BIGINT; camp_fit BIGINT;
    vo_tickets BIGINT; vo_explorer BIGINT; vo_fit BIGINT;
    rep_venue BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE lower(email) = 'admin@ayojon.dev') THEN
        RAISE NOTICE 'Ayojon demo data already present — skipping';
        RETURN;
    END IF;

    fri := today + 1 + ((4 - extract(dow FROM today)::int + 7) % 7);
    sat := fri + 1;

    -- ------------------------------------------------------------------
    -- Users
    -- ------------------------------------------------------------------
    INSERT INTO users (name, email, password_hash, role) VALUES ('Ayojon Admin', 'admin@ayojon.dev', pw, 'ADMIN') RETURNING user_id INTO admin;
    INSERT INTO users (name, email, password_hash, role) VALUES ('Dhaka Beats Collective', 'beats@ayojon.dev', pw, 'ORGANIZER') RETURNING user_id INTO org_beats;
    INSERT INTO users (name, email, password_hash, role) VALUES ('TechHub Dhaka', 'techhub@ayojon.dev', pw, 'ORGANIZER') RETURNING user_id INTO org_tech;
    INSERT INTO users (name, email, password_hash, role) VALUES ('Foodies of Dhaka', 'foodies@ayojon.dev', pw, 'ORGANIZER') RETURNING user_id INTO org_food;
    INSERT INTO users (name, email, password_hash) VALUES ('Rafi Hasan',     'rafi@ayojon.dev',   pw) RETURNING user_id INTO rafi;
    INSERT INTO users (name, email, password_hash) VALUES ('Nabila Rahman',  'nabila@ayojon.dev', pw) RETURNING user_id INTO nabila;
    INSERT INTO users (name, email, password_hash) VALUES ('Tanvir Ahmed',   'tanvir@ayojon.dev', pw) RETURNING user_id INTO tanvir;
    INSERT INTO users (name, email, password_hash) VALUES ('Sadia Chowdhury','sadia@ayojon.dev',  pw) RETURNING user_id INTO sadia;
    INSERT INTO users (name, email, password_hash) VALUES ('Arif Hossain',   'arif@ayojon.dev',   pw) RETURNING user_id INTO arif;

    -- ------------------------------------------------------------------
    -- Venues (real places, approximate coordinates)
    -- ------------------------------------------------------------------
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Hatirjheel'), 'Hatirjheel Amphitheatre', 'Hatirjheel Lake, Dhaka 1219', 23.7622000, 90.4044000)
    RETURNING venue_id INTO v_hatir;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Agargaon'), 'BICC Hall of Fame', 'Sher-e-Bangla Nagar, Agargaon, Dhaka 1207', 23.7728000, 90.3803000)
    RETURNING venue_id INTO v_bicc;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Old Dhaka'), 'Lalbagh Fort', 'Lalbagh Road, Old Dhaka, Dhaka 1211', 23.7190000, 90.3881000)
    RETURNING venue_id INTO v_lalbagh;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Shahbagh'), 'TSC, University of Dhaka', 'Teacher-Student Centre, Nilkhet Road, Dhaka 1000', 23.7317000, 90.3953000)
    RETURNING venue_id INTO v_tsc;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Dhanmondi'), 'Rabindra Sarobar', 'Dhanmondi Lake, Road 8A, Dhaka 1209', 23.7448000, 90.3760000)
    RETURNING venue_id INTO v_sarobar;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Dhanmondi'), 'Bengal Shilpalaya', 'House 42, Road 27 (old), Dhanmondi, Dhaka 1209', 23.7537000, 90.3712000)
    RETURNING venue_id INTO v_shilpalaya;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Gulshan'), 'Gulshan Society Lake Park', 'Road 104, Gulshan 2, Dhaka 1212', 23.7966000, 90.4142000)
    RETURNING venue_id INTO v_gulshan;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Bashundhara'), 'International Convention City Bashundhara (ICCB)', 'Kuril, Bashundhara R/A, Dhaka 1229', 23.8164000, 90.4239000)
    RETURNING venue_id INTO v_iccb;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Shahbagh'), 'Bangladesh Shilpakala Academy', 'Segunbagicha, Dhaka 1000', 23.7360000, 90.4050000)
    RETURNING venue_id INTO v_shilpakala;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Mirpur'), 'Sher-e-Bangla National Cricket Stadium', 'Mirpur 6, Dhaka 1216', 23.8068000, 90.3634000)
    RETURNING venue_id INTO v_mirpur;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Uttara'), 'Diabari Lake Park', 'Sector 15, Uttara, Dhaka 1230', 23.8817000, 90.3720000)
    RETURNING venue_id INTO v_uttara;
    INSERT INTO venue (area_id, venue_name, venue_address, latitude, longitude)
    VALUES ((SELECT area_id FROM area WHERE area_name = 'Banani'), 'Banani Rooftop Stage', 'Road 11, Banani, Dhaka 1213', 23.7940000, 90.4030000)
    RETURNING venue_id INTO v_banani;

    -- ------------------------------------------------------------------
    -- Events — all submitted as PENDING_REVIEW, then moderated below.
    -- Times are Dhaka wall-clock: (date + time) AT TIME ZONE 'Asia/Dhaka'.
    -- ------------------------------------------------------------------
    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url, official_source_url, check_in_points)
    VALUES (org_beats, (SELECT category_id FROM event_category WHERE category_name = 'Music'), v_hatir,
            'Hatirjheel Sunset Acoustic Night',
            'Unplugged sets from Dhaka''s indie bands by the lake as the sun goes down. Bring a mat and friends.',
            ((today - 20) + TIME '17:30') AT TIME ZONE 'Asia/Dhaka', ((today - 20) + TIME '21:00') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/hatirjheel-acoustic', 'https://facebook.com/dhakabeats', 10)
    RETURNING event_id INTO e_acoustic;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url, official_source_url, check_in_points)
    VALUES (org_tech, (SELECT category_id FROM event_category WHERE category_name = 'Technology'), v_bicc,
            'Dhaka Tech Meetup: AI in Bangladesh',
            'Talks and panels on building AI products for Bangla speakers, with demos from local startups.',
            ((today - 12) + TIME '10:00') AT TIME ZONE 'Asia/Dhaka', ((today - 12) + TIME '16:00') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/dhaka-ai-meetup', 'https://techhubdhaka.example.com/events/ai', 25)
    RETURNING event_id INTO e_ai;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url, official_source_url, check_in_points)
    VALUES (org_food, (SELECT category_id FROM event_category WHERE category_name = 'Food & Drink'), v_lalbagh,
            'Old Dhaka Heritage Food Walk',
            'A guided walk from Lalbagh Fort through Chawkbazar tasting kacchi, bakarkhani and borhani.',
            ((today - 6) + TIME '15:00') AT TIME ZONE 'Asia/Dhaka', ((today - 6) + TIME '19:00') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/old-dhaka-food-walk', 'https://foodiesofdhaka.example.com/walks', 15)
    RETURNING event_id INTO e_foodwalk;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, official_source_url)
    VALUES (org_beats, (SELECT category_id FROM event_category WHERE category_name = 'Arts & Culture'), v_tsc,
            'TSC Open Mic & Poetry Evening',
            'Open stage for poetry, spoken word and acoustic music. Sign up at the gate.',
            ((today - 3) + TIME '17:00') AT TIME ZONE 'Asia/Dhaka', ((today - 3) + TIME '20:00') AT TIME ZONE 'Asia/Dhaka',
            'https://facebook.com/dhakabeats')
    RETURNING event_id INTO e_openmic;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url, official_source_url)
    VALUES (org_food, (SELECT category_id FROM event_category WHERE category_name = 'Food & Drink'), v_sarobar,
            'Dhanmondi Lake Food Carnival',
            'Forty stalls of street food, fuchka face-offs and live music around Rabindra Sarobar.',
            (fri + TIME '16:00') AT TIME ZONE 'Asia/Dhaka', (fri + TIME '22:00') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/dhanmondi-food-carnival', 'https://foodiesofdhaka.example.com/carnival')
    RETURNING event_id INTO e_carnival;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url, official_source_url)
    VALUES (org_beats, (SELECT category_id FROM event_category WHERE category_name = 'Music'), v_shilpalaya,
            'Bengal Classical Music Night',
            'An evening of khayal and sitar with renowned artists. Limited seating.',
            (fri + TIME '19:00') AT TIME ZONE 'Asia/Dhaka', (fri + TIME '22:30') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/classical-night', 'https://bengalfoundation.example.com/classical')
    RETURNING event_id INTO e_classical;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url)
    VALUES (org_tech, (SELECT category_id FROM event_category WHERE category_name = 'Sports & Fitness'), v_hatir,
            'Hatirjheel Fun Run 5K',
            'A morning 5K loop around Hatirjheel for all levels. Medal and breakfast for finishers.',
            (sat + TIME '06:30') AT TIME ZONE 'Asia/Dhaka', (sat + TIME '09:30') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/hatirjheel-5k')
    RETURNING event_id INTO e_run;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time)
    VALUES (org_beats, (SELECT category_id FROM event_category WHERE category_name = 'Exhibitions & Fairs'), v_gulshan,
            'Gulshan Indie Photography Exhibition',
            'Open-air exhibition of street photography from 30 young Bangladeshi photographers.',
            (sat + TIME '15:00') AT TIME ZONE 'Asia/Dhaka', (sat + TIME '20:00') AT TIME ZONE 'Asia/Dhaka')
    RETURNING event_id INTO e_photo;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url, official_source_url, check_in_points)
    VALUES (org_tech, (SELECT category_id FROM event_category WHERE category_name = 'Business & Networking'), v_iccb,
            'Startup Pitch Night Dhaka',
            'Ten early-stage startups pitch to investors. Networking dinner afterwards.',
            ((today + 10) + TIME '17:00') AT TIME ZONE 'Asia/Dhaka', ((today + 10) + TIME '21:00') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/pitch-night', 'https://techhubdhaka.example.com/events/pitch-night', 20)
    RETURNING event_id INTO e_pitch;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url, official_source_url, check_in_points)
    VALUES (org_tech, (SELECT category_id FROM event_category WHERE category_name = 'Technology'), v_bicc,
            'Hackathon: Smart Dhaka 2026',
            '36-hour hackathon on traffic, flooding and waste problems in Dhaka. Teams of 2-4.',
            ((today + 14) + TIME '09:00') AT TIME ZONE 'Asia/Dhaka', ((today + 15) + TIME '21:00') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/smart-dhaka-hack', 'https://techhubdhaka.example.com/hackathon', 30)
    RETURNING event_id INTO e_hack;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url)
    VALUES (org_beats, (SELECT category_id FROM event_category WHERE category_name = 'Festivals'), v_shilpakala,
            'Winter Folk Fest',
            'Baul, bhawaiya and jari gaan performances with pitha stalls and handicraft fair.',
            ((today + 21) + TIME '15:00') AT TIME ZONE 'Asia/Dhaka', ((today + 21) + TIME '22:00') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/winter-folk-fest')
    RETURNING event_id INTO e_folk;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time)
    VALUES (org_tech, (SELECT category_id FROM event_category WHERE category_name = 'Sports & Fitness'), v_mirpur,
            'Mirpur Street Cricket Championship',
            'Tape-ball tournament for neighbourhood teams. Register your team of 8.',
            ((today + 18) + TIME '08:00') AT TIME ZONE 'Asia/Dhaka', ((today + 18) + TIME '17:00') AT TIME ZONE 'Asia/Dhaka')
    RETURNING event_id INTO e_cricket;

    -- Overlaps Startup Pitch Night at ICCB -> shows up in v_venue_event_overlap (F11).
    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time)
    VALUES (org_food, (SELECT category_id FROM event_category WHERE category_name = 'Business & Networking'), v_iccb,
            'Bashundhara Career Fair',
            'Meet 50+ employers hiring graduates in tech, banking and FMCG.',
            ((today + 10) + TIME '14:00') AT TIME ZONE 'Asia/Dhaka', ((today + 10) + TIME '19:00') AT TIME ZONE 'Asia/Dhaka')
    RETURNING event_id INTO e_career;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time)
    VALUES (org_tech, (SELECT category_id FROM event_category WHERE category_name = 'Education & Workshops'), v_uttara,
            'Uttara Kids Science Workshop',
            'Hands-on experiments for kids aged 8-12.',
            ((today + 25) + TIME '10:00') AT TIME ZONE 'Asia/Dhaka', ((today + 25) + TIME '13:00') AT TIME ZONE 'Asia/Dhaka')
    RETURNING event_id INTO e_kids;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url)
    VALUES (org_beats, (SELECT category_id FROM event_category WHERE category_name = 'Film & Theatre'), v_banani,
            'Banani Rooftop Comedy Night',
            'Stand-up comedy in Bangla and English from Dhaka''s best new comics.',
            ((today + 5) + TIME '20:00') AT TIME ZONE 'Asia/Dhaka', ((today + 5) + TIME '22:30') AT TIME ZONE 'Asia/Dhaka',
            'https://tickets.example.com/rooftop-comedy')
    RETURNING event_id INTO e_comedy;

    INSERT INTO event (organizer_id, category_id, venue_id, title, description, start_time, end_time, ticket_url)
    VALUES (org_food, (SELECT category_id FROM event_category WHERE category_name = 'Music'), v_iccb,
            'FREE iPhone Giveaway Mega Concert',
            'Pay 99 tk registration and win a free iPhone!!! Limited slots!!!',
            ((today + 30) + TIME '18:00') AT TIME ZONE 'Asia/Dhaka', ((today + 30) + TIME '23:00') AT TIME ZONE 'Asia/Dhaka',
            'http://free-iphone-concert.example.net/pay')
    RETURNING event_id INTO e_scam;

    -- Ticket tiers (F5 price filter / F6 profile)
    INSERT INTO ticket_tier (event_id, tier_name, price) VALUES
        (e_acoustic, 'General', 300), (e_acoustic, 'Front Row', 800),
        (e_ai, 'Free Pass', 0),
        (e_foodwalk, 'Walk + Tasting', 800),
        (e_openmic, 'Free Entry', 0),
        (e_carnival, 'Entry', 0), (e_carnival, 'VIP Tasting Pass', 500),
        (e_classical, 'Balcony', 500), (e_classical, 'Front Stalls', 1500),
        (e_run, 'Runner Kit', 350),
        (e_photo, 'Free Entry', 0),
        (e_pitch, 'Attendee', 200), (e_pitch, 'Investor Table', 5000),
        (e_hack, 'Team Registration', 0),
        (e_folk, 'General', 300), (e_folk, 'Family (4)', 1000),
        (e_cricket, 'Team Entry', 1200),
        (e_career, 'Free Entry', 0),
        (e_kids, 'Child Seat', 600),
        (e_comedy, 'Standard', 700),
        (e_scam, 'Registration', 99);

    -- Posters (placeholder images)
    INSERT INTO event_media (event_id, media_url, media_type)
    SELECT event_id, 'https://placehold.co/600x800/png?text=' || replace(title, ' ', '+'), 'POSTER'
      FROM event WHERE organizer_id IN (org_beats, org_tech, org_food);
    INSERT INTO event_media (event_id, media_url, media_type, sort_order) VALUES
        (e_carnival, 'https://placehold.co/1200x800/png?text=Food+Stalls', 'IMAGE', 1),
        (e_carnival, 'https://placehold.co/1200x800/png?text=Fuchka+Face-off', 'IMAGE', 2),
        (e_photo,    'https://placehold.co/1200x800/png?text=Gallery+Preview', 'IMAGE', 1);

    -- Tags (F16 / F19)
    INSERT INTO event_tag (event_id, tag_id)
    SELECT x.event_id, t.tag_id
      FROM (VALUES (e_acoustic, 'live-music'), (e_acoustic, 'outdoor'), (e_acoustic, 'night'),
                   (e_ai, 'tech'), (e_ai, 'networking'), (e_ai, 'students'),
                   (e_foodwalk, 'street-food'), (e_foodwalk, 'cultural'), (e_foodwalk, 'outdoor'),
                   (e_openmic, 'art'), (e_openmic, 'students'), (e_openmic, 'free-entry'),
                   (e_carnival, 'street-food'), (e_carnival, 'outdoor'), (e_carnival, 'family-friendly'), (e_carnival, 'free-entry'),
                   (e_classical, 'live-music'), (e_classical, 'cultural'), (e_classical, 'indoor'), (e_classical, 'night'),
                   (e_run, 'running'), (e_run, 'outdoor'),
                   (e_photo, 'photography'), (e_photo, 'art'), (e_photo, 'free-entry'),
                   (e_pitch, 'startup'), (e_pitch, 'networking'), (e_pitch, 'tech'),
                   (e_hack, 'tech'), (e_hack, 'students'), (e_hack, 'workshop'),
                   (e_folk, 'cultural'), (e_folk, 'live-music'), (e_folk, 'family-friendly'),
                   (e_cricket, 'outdoor'),
                   (e_career, 'networking'), (e_career, 'students'),
                   (e_kids, 'kids'), (e_kids, 'workshop'),
                   (e_comedy, 'comedy'), (e_comedy, 'night')) AS x (event_id, tag_name)
      JOIN tag t ON t.tag_name = x.tag_name;

    -- ------------------------------------------------------------------
    -- F18 moderation: approve most, request revision, reject the scam.
    -- (Bashundhara Career Fair + Mirpur Cricket stay PENDING_REVIEW.)
    -- ------------------------------------------------------------------
    INSERT INTO event_submission_review (event_id, admin_id, decision, comment)
    SELECT unnest(ARRAY[e_acoustic, e_ai, e_foodwalk, e_openmic, e_carnival, e_classical,
                        e_run, e_photo, e_pitch, e_hack, e_folk, e_comedy]),
           admin, 'APPROVED', 'Looks good.';
    INSERT INTO event_submission_review (event_id, admin_id, decision, comment)
    VALUES (e_kids, admin, 'REVISION_REQUESTED', 'Please add the ticket link and an age-appropriate safety note.'),
           (e_scam, admin, 'REJECTED', 'Misleading giveaway; payment link is not an official ticketing partner.');

    -- F7 verification against official sources (Open Mic stays unverified)
    INSERT INTO event_verification (event_id, admin_id, decision, source_url, notes) VALUES
        (e_acoustic,  admin, 'VERIFIED', 'https://facebook.com/dhakabeats', 'Matches organizer page.'),
        (e_ai,        admin, 'VERIFIED', 'https://techhubdhaka.example.com/events/ai', 'Confirmed with venue.'),
        (e_foodwalk,  admin, 'VERIFIED', 'https://foodiesofdhaka.example.com/walks', NULL),
        (e_carnival,  admin, 'VERIFIED', 'https://foodiesofdhaka.example.com/carnival', NULL),
        (e_classical, admin, 'VERIFIED', 'https://bengalfoundation.example.com/classical', NULL),
        (e_pitch,     admin, 'VERIFIED', 'https://techhubdhaka.example.com/events/pitch-night', NULL),
        (e_hack,      admin, 'VERIFIED', 'https://techhubdhaka.example.com/hackathon', NULL);

    -- ------------------------------------------------------------------
    -- F3 check-ins at past events (points only from verified ones)
    -- Rafi: 10 + 25 + 15 = 50 -> "First Steps" + "Explorer"
    -- ------------------------------------------------------------------
    INSERT INTO attendance (user_id, event_id, checked_in_at)
    SELECT x.user_id, x.event_id, e.start_time + x.after_start
      FROM (VALUES (rafi,   e_acoustic, INTERVAL '20 min'), (rafi,   e_ai, INTERVAL '35 min'),
                   (rafi,   e_foodwalk, INTERVAL '5 min'),  (rafi,   e_openmic, INTERVAL '40 min'),
                   (nabila, e_acoustic, INTERVAL '50 min'), (nabila, e_foodwalk, INTERVAL '10 min'),
                   (tanvir, e_ai, INTERVAL '15 min'),       (tanvir, e_openmic, INTERVAL '5 min'),
                   (sadia,  e_acoustic, INTERVAL '30 min')) AS x (user_id, event_id, after_start)
      JOIN event e ON e.event_id = x.event_id;

    -- F15 reviews (only possible because these users checked in)
    INSERT INTO review (user_id, event_id, rating, review_text) VALUES
        (rafi,   e_acoustic, 5, 'Perfect sunset vibe, great sound for an outdoor show.'),
        (rafi,   e_ai,       4, 'Solid talks, venue Wi-Fi struggled a bit.'),
        (rafi,   e_foodwalk, 5, 'Best kacchi of my life. Guide knew every lane.'),
        (nabila, e_acoustic, 4, 'Lovely evening, a bit crowded near the stage.'),
        (nabila, e_foodwalk, 5, 'Worth every taka.'),
        (tanvir, e_openmic,  3, 'Fun, but started 40 minutes late.');

    -- ------------------------------------------------------------------
    -- F9 collections (auto-bookmark) and F8 recent bookmarks for trending
    -- ------------------------------------------------------------------
    INSERT INTO collection (user_id, collection_name, description)
    VALUES (rafi, 'Weekend Plans', 'Stuff to do this weekend') RETURNING collection_id INTO c_rafi_weekend;
    INSERT INTO collection (user_id, collection_name, collection_type, is_private)
    VALUES (rafi, 'Someday Wishlist', 'WISHLIST', TRUE) RETURNING collection_id INTO c_rafi_wish;
    INSERT INTO collection (user_id, collection_name)
    VALUES (nabila, 'Food Hunts') RETURNING collection_id INTO c_nabila;

    INSERT INTO collection_item (collection_id, event_id, note) VALUES
        (c_rafi_weekend, e_carnival, 'Go early for fuchka'),
        (c_rafi_weekend, e_classical, NULL),
        (c_rafi_weekend, e_run, 'Register before Thursday'),
        (c_rafi_weekend, e_photo, NULL),
        (c_rafi_wish, e_hack, 'Find a team'),
        (c_nabila, e_carnival, NULL);

    INSERT INTO bookmark (user_id, event_id, bookmarked_at) VALUES
        (nabila, e_classical, now() - INTERVAL '2 days'),
        (nabila, e_folk,      now() - INTERVAL '1 day'),
        (tanvir, e_pitch,     now() - INTERVAL '3 days'),
        (tanvir, e_hack,      now() - INTERVAL '2 days'),
        (tanvir, e_carnival,  now() - INTERVAL '1 day'),
        (sadia,  e_carnival,  now() - INTERVAL '5 hours'),
        (sadia,  e_comedy,    now() - INTERVAL '4 days'),
        (sadia,  e_photo,     now() - INTERVAL '6 days'),
        (arif,   e_pitch,     now() - INTERVAL '6 days'),
        (arif,   e_hack,      now() - INTERVAL '1 day'),
        (arif,   e_carnival,  now() - INTERVAL '2 days'),
        (arif,   e_folk,      now() - INTERVAL '12 days')
    ON CONFLICT DO NOTHING;

    -- ------------------------------------------------------------------
    -- F12 follows, then F17 edits that notify followers
    -- ------------------------------------------------------------------
    INSERT INTO event_follow (user_id, event_id) VALUES
        (rafi, e_pitch), (tanvir, e_pitch), (arif, e_pitch),
        (sadia, e_comedy), (rafi, e_comedy),
        (nabila, e_carnival), (rafi, e_carnival);

    UPDATE event
       SET start_time = start_time + INTERVAL '1 hour',
           end_time   = end_time   + INTERVAL '1 hour',
           updated_by = org_tech
     WHERE event_id = e_pitch;

    UPDATE event
       SET status = 'CANCELLED',
           cancellation_reason = 'The headliner is unwell. All tickets will be refunded.',
           updated_by = org_beats
     WHERE event_id = e_comedy;

    -- ------------------------------------------------------------------
    -- F10 / F11 weekend itinerary (Carnival and Classical Night overlap)
    -- ------------------------------------------------------------------
    INSERT INTO itinerary (user_id, title, start_date, end_date, notes)
    VALUES (rafi, 'This Weekend in Dhaka', fri, sat, 'Leave Saturday evening free if the run wipes me out')
    RETURNING itinerary_id INTO it_rafi;

    INSERT INTO itinerary_item (itinerary_id, event_id, planned_start, planned_end) VALUES
        (it_rafi, e_carnival,  NULL, NULL),
        (it_rafi, e_classical, NULL, NULL),
        (it_rafi, e_run,       NULL, NULL),
        (it_rafi, e_photo, (sat + TIME '16:00') AT TIME ZONE 'Asia/Dhaka', (sat + TIME '18:00') AT TIME ZONE 'Asia/Dhaka');

    -- ------------------------------------------------------------------
    -- F14 dispute reports
    -- ------------------------------------------------------------------
    INSERT INTO dispute_report (reporter_id, event_id, reason_id, details)
    VALUES (nabila, e_scam, (SELECT reason_id FROM dispute_reason WHERE reason_code = 'SCAM_OR_FRAUD'),
            'Asks for bKash payment to a personal number for a "free" iPhone.');
    INSERT INTO dispute_report (reporter_id, event_id, reason_id, details)
    VALUES (arif, e_cricket, (SELECT reason_id FROM dispute_reason WHERE reason_code = 'INACCURATE_VENUE'),
            'The stadium does not host street cricket; the matches are in the field behind it.');
    INSERT INTO dispute_report (reporter_id, event_id, reason_id, details)
    VALUES (tanvir, e_openmic, (SELECT reason_id FROM dispute_reason WHERE reason_code = 'INACCURATE_DETAILS'),
            'Listed start time was 5 PM but it started at 5:40 PM.')
    RETURNING report_id INTO rep_venue;

    UPDATE dispute_report
       SET status = 'RESOLVED', resolved_by = admin,
           resolution_note = 'Organizer has been asked to post accurate start times.'
     WHERE report_id = rep_venue;

    -- ------------------------------------------------------------------
    -- F4 sponsors, ad campaigns, vouchers, claims
    -- ------------------------------------------------------------------
    INSERT INTO sponsor (sponsor_name, website_url, logo_url, contact_email) VALUES
        ('Nagar Tickets',   'https://nagartickets.example.com', 'https://placehold.co/200x200/png?text=Nagar', 'partners@nagartickets.example.com'),
        ('Chaa & Co. Cafe', 'https://chaaandco.example.com',    'https://placehold.co/200x200/png?text=Chaa',  'hello@chaaandco.example.com'),
        ('Dhaka Fit Gear',  'https://dhakafit.example.com',     'https://placehold.co/200x200/png?text=Fit',   'ads@dhakafit.example.com');

    INSERT INTO ad_campaign (sponsor_id, campaign_name, description, banner_url, target_url, starts_at, ends_at)
    VALUES ((SELECT sponsor_id FROM sponsor WHERE sponsor_name = 'Nagar Tickets'), 'Winter Events Season',
            'Discounts on concert and festival tickets booked through Nagar Tickets.',
            'https://placehold.co/1200x300/png?text=Winter+Season', 'https://nagartickets.example.com/winter',
            now() - INTERVAL '7 days', now() + INTERVAL '60 days')
    RETURNING campaign_id INTO camp_tickets;
    INSERT INTO ad_campaign (sponsor_id, campaign_name, description, target_url, starts_at, ends_at)
    VALUES ((SELECT sponsor_id FROM sponsor WHERE sponsor_name = 'Dhaka Fit Gear'), 'Run Dhaka',
            'Gear discounts for registered runners.', 'https://dhakafit.example.com/run',
            now() - INTERVAL '3 days', now() + INTERVAL '30 days')
    RETURNING campaign_id INTO camp_fit;

    INSERT INTO voucher (campaign_id, promo_code, description, discount_percent, max_discount_amount, min_points_required, total_quantity, booking_url, valid_from, valid_until)
    VALUES (camp_tickets, 'AYOJON-WINTER20', '20% off any festival ticket on Nagar Tickets', 20, 300, 0, 200,
            'https://nagartickets.example.com/winter', now() - INTERVAL '7 days', now() + INTERVAL '60 days')
    RETURNING voucher_id INTO vo_tickets;
    INSERT INTO voucher (campaign_id, promo_code, description, discount_percent, min_points_required, total_quantity, booking_url, valid_from, valid_until)
    VALUES (camp_tickets, 'EXPLORER-35', '35% off for Explorer badge holders (50+ points)', 35, 50, 25,
            'https://nagartickets.example.com/explorer', now() - INTERVAL '7 days', now() + INTERVAL '45 days')
    RETURNING voucher_id INTO vo_explorer;
    INSERT INTO voucher (campaign_id, event_id, promo_code, description, discount_percent, total_quantity, booking_url, valid_from, valid_until)
    VALUES (camp_fit, e_run, 'RUN5K-15', '15% off running shoes for Hatirjheel 5K runners', 15, 100,
            'https://dhakafit.example.com/run', now() - INTERVAL '3 days', now() + INTERVAL '30 days')
    RETURNING voucher_id INTO vo_fit;

    INSERT INTO voucher_claim (user_id, voucher_id) VALUES
        (rafi, vo_tickets), (rafi, vo_explorer), (nabila, vo_tickets), (tanvir, vo_fit);
    UPDATE voucher_claim SET redeemed_at = now() WHERE user_id = nabila AND voucher_id = vo_tickets;

    RAISE NOTICE 'Ayojon demo data loaded';
END $$;

-- =====================================================================
-- Ayojon — V4: reference / lookup data needed in every environment
-- (demo users & events live separately in db/seed/R__demo_data.sql)
-- =====================================================================

INSERT INTO event_category (category_name, description) VALUES
    ('Music',                  'Concerts, gigs and live performances'),
    ('Arts & Culture',         'Cultural programmes, heritage and traditional arts'),
    ('Technology',             'Tech talks, hackathons and developer meetups'),
    ('Food & Drink',           'Food festivals, pop-ups and tastings'),
    ('Sports & Fitness',       'Tournaments, runs and fitness sessions'),
    ('Education & Workshops',  'Seminars, classes and hands-on workshops'),
    ('Business & Networking',  'Startup events, conferences and networking'),
    ('Festivals',              'Seasonal and national festivals'),
    ('Film & Theatre',         'Screenings, plays and stand-up comedy'),
    ('Community & Charity',    'Volunteering, fundraisers and community drives'),
    ('Exhibitions & Fairs',    'Book fairs, trade fairs and gallery exhibitions');

-- Dhaka neighbourhoods with approximate centre points.
INSERT INTO area (area_name, city, latitude, longitude) VALUES
    ('Gulshan',          'Dhaka', 23.7925000, 90.4078000),
    ('Banani',           'Dhaka', 23.7940000, 90.4043000),
    ('Dhanmondi',        'Dhaka', 23.7461000, 90.3742000),
    ('Hatirjheel',       'Dhaka', 23.7580000, 90.4050000),
    ('Shahbagh',         'Dhaka', 23.7383000, 90.3958000),
    ('Motijheel',        'Dhaka', 23.7330000, 90.4172000),
    ('Tejgaon',          'Dhaka', 23.7639000, 90.3926000),
    ('Agargaon',         'Dhaka', 23.7781000, 90.3790000),
    ('Mohammadpur',      'Dhaka', 23.7662000, 90.3589000),
    ('Mirpur',           'Dhaka', 23.8223000, 90.3654000),
    ('Uttara',           'Dhaka', 23.8759000, 90.3795000),
    ('Bashundhara',      'Dhaka', 23.8193000, 90.4526000),
    ('Old Dhaka',        'Dhaka', 23.7104000, 90.4074000),
    ('Purbachal',        'Dhaka', 23.8414000, 90.5135000);

INSERT INTO tag (tag_name) VALUES
    ('outdoor'), ('indoor'), ('family-friendly'), ('free-entry'), ('live-music'),
    ('students'), ('networking'), ('workshop'), ('street-food'), ('night'),
    ('charity'), ('art'), ('startup'), ('cultural'), ('books'), ('running'),
    ('photography'), ('comedy'), ('kids'), ('tech');

INSERT INTO dispute_reason (reason_code, reason_name) VALUES
    ('SPAM',                'Spam or duplicate listing'),
    ('COUNTERFEIT_LISTING', 'Counterfeit / fake event'),
    ('INACCURATE_VENUE',    'Inaccurate venue details'),
    ('INACCURATE_DETAILS',  'Wrong date, time or price'),
    ('SCAM_OR_FRAUD',       'Ticket scam or fraud'),
    ('OFFENSIVE_CONTENT',   'Offensive or inappropriate content'),
    ('OTHER',               'Other');

INSERT INTO badge (badge_name, description, milestone_points) VALUES
    ('First Steps',  'Checked in to your first verified event',  10),
    ('Explorer',     'Earned 50 attendance points',              50),
    ('Regular',      'Earned 100 attendance points',            100),
    ('Enthusiast',   'Earned 250 attendance points',            250),
    ('City Legend',  'Earned 500 attendance points',            500);

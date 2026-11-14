package com.ayojon.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * All SQL for the event table.
 *
 * Two kinds of access:
 * - Organizer/admin (findById, findByOrganizerId, findPendingReview, findAll, create, update):
 *   sees every status, including DRAFT and rejected (CANCELLED) submissions.
 * - Public discovery, F5/F6 (search, findPublicById): sees only PUBLIC_STATUS events.
 *   A rejected submission is stored as CANCELLED and a draft as DRAFT, so neither can be
 *   found through search or the public detail page.
 */
@Repository
public class EventRepository {
    /** The only status that search and the public detail page may show. */
    static final String PUBLIC_STATUS = "PUBLISHED";
    static final int MAX_RESULTS = 100;

    private final JdbcTemplate jdbc;

    public EventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- organizer / admin

    // FLAT-SCHEMA: venue name, area and poster come straight from the event table
    private static final String BASE_SELECT = """
            SELECT e.event_id, e.organizer_id, u.name AS organizer_name,
                   ec.category_name, e.venue_name, e.area AS area_name,
                   e.title, e.description,
                   e.start_time, e.end_time,
                   e.ticket_url, e.poster_url, e.status, e.created_at,
                   CASE
                       WHEN cl.last_changed IS NOT NULL AND lr.verified_at IS NOT NULL AND cl.last_changed > lr.verified_at THEN 'RESUBMITTED'
                       ELSE lr.decision
                   END AS latest_decision,
                   lr.source_url AS latest_note
            FROM event e
            JOIN users u ON e.organizer_id = u.user_id
            JOIN event_category ec ON e.category_id = ec.category_id
            LEFT JOIN (
                SELECT DISTINCT ON (event_id) event_id, decision, source_url, verified_at
                FROM event_verification
                ORDER BY event_id, verified_at DESC
            ) lr ON lr.event_id = e.event_id
            LEFT JOIN (
                SELECT event_id, MAX(changed_at) AS last_changed
                FROM event_change_log
                GROUP BY event_id
            ) cl ON cl.event_id = e.event_id
            """;

    private final RowMapper<Event> mapper = (row, index) -> new Event(
            row.getInt("event_id"),
            row.getInt("organizer_id"),
            row.getString("organizer_name"),
            row.getString("category_name"),
            row.getString("venue_name"),
            row.getString("area_name"),
            row.getString("title"),
            row.getString("description"),
            row.getString("start_time"),
            row.getString("end_time"),
            row.getString("ticket_url"),
            row.getString("status"),
            row.getString("created_at"),
            row.getString("latest_decision"),
            row.getString("latest_note"),
            row.getString("poster_url"));

    public List<Event> findByOrganizerId(int organizerId) {
        return jdbc.query(BASE_SELECT + " WHERE e.organizer_id = ? ORDER BY e.created_at DESC",
                mapper, organizerId);
    }

    public List<Event> findPendingReview() {
        return jdbc.query(BASE_SELECT + " WHERE e.status = 'DRAFT' ORDER BY e.created_at ASC", mapper);
    }

    public List<Event> findAll() {
        return jdbc.query(BASE_SELECT + " ORDER BY e.created_at DESC", mapper);
    }

    /** Any status. For organizer editing and admin review only; public pages use findPublicById. */
    public Optional<Event> findById(int eventId) {
        return jdbc.query(BASE_SELECT + " WHERE e.event_id = ?", mapper, eventId)
                .stream().findFirst();
    }

    @Transactional
    public void create(int organizerId, String categoryName, String venueName,
                       String venueAddress, String areaName, double latitude, double longitude,
                       String title, String description,
                       Timestamp startTime, Timestamp endTime, String ticketUrl,
                       String posterUrl) {
        int categoryId = resolveCategory(categoryName);

        jdbc.update("INSERT INTO event(organizer_id, category_id, title, description, " +
                        "area, venue_name, venue_address, latitude, longitude, " +
                        "start_time, end_time, ticket_url, poster_url, status) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                organizerId, categoryId, title, description,
                areaName, venueName, venueAddress, latitude, longitude,
                startTime, endTime, ticketUrl, posterUrl, "DRAFT");
    }

    @Transactional
    public void update(int eventId, int organizerId, String categoryName, String venueName,
                       String venueAddress, String areaName, double latitude, double longitude,
                       String title, String description,
                       Timestamp startTime, Timestamp endTime, String ticketUrl,
                       String posterUrl) {
        int categoryId = resolveCategory(categoryName);

        // One draft-only UPDATE changes the details and the poster together
        int updated = jdbc.update("UPDATE event SET category_id = ?, title = ?, description = ?, " +
                        "area = ?, venue_name = ?, venue_address = ?, latitude = ?, longitude = ?, " +
                        "start_time = ?, end_time = ?, ticket_url = ?, poster_url = ? " +
                        "WHERE event_id = ? AND status = 'DRAFT'",
                categoryId, title, description, areaName, venueName, venueAddress, latitude, longitude,
                startTime, endTime, ticketUrl, posterUrl, eventId);

        // RACE FIX: if an admin already reviewed the event, no row was updated. Throwing here rolls back
        // the whole transaction (including the category insert above), so no change log row is written
        // and nothing else changes. The controller turns this into a 409 conflict.
        if (updated == 0) {
            throw new EventNotEditableException(eventId);
        }

        jdbc.update("INSERT INTO event_change_log(event_id, changed_by, change_description) VALUES(?,?,?)",
                eventId, organizerId, "Updated event details after revision request");
    }

    public void updateStatus(int eventId, String status) {
        jdbc.update("UPDATE event SET status = ? WHERE event_id = ?", status, eventId);
    }

    private int resolveCategory(String categoryName) {
        jdbc.update("INSERT INTO event_category(category_name) VALUES(?) ON CONFLICT(category_name) DO NOTHING",
                categoryName);
        return jdbc.queryForObject("SELECT category_id FROM event_category WHERE category_name = ?",
                Integer.class, categoryName);
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM event", Integer.class);
        return count != null ? count : 0;
    }

    public int countPending() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM event WHERE status = 'DRAFT'", Integer.class);
        return count != null ? count : 0;
    }

    // ---------------------------------------------------------------- public discovery (F5, F6)

    // Event times are stored as Dhaka local time, so "now" must be Dhaka time too.
    // The poster is event.poster_url, the same column organizers write when submitting an event.
    static final String SEARCH = """
            SELECT e.event_id, e.title, c.category_name, e.area, e.venue_name, e.start_time, e.end_time,
                   e.base_price, e.poster_url, e.is_verified
            FROM event e JOIN event_category c ON c.category_id = e.category_id
            WHERE e.status = ? AND e.end_time >= (now() AT TIME ZONE 'Asia/Dhaka')""";

    static final String PUBLIC_DETAILS = """
            SELECT e.event_id, e.title, e.description, c.category_name, e.area, e.venue_name, e.venue_address,
                   e.start_time, e.end_time, e.base_price, e.poster_url, e.ticket_url, e.is_verified,
                   u.name AS organizer_name
            FROM event e
            JOIN event_category c ON c.category_id = e.category_id
            JOIN users u ON u.user_id = e.organizer_id
            WHERE e.event_id = ? AND e.status = ?""";

    public List<EventSummary> search(EventFilter filter) {
        Query query = searchQuery(filter);
        return jdbc.query(query.sql(), (row, index) -> toSummary(row), query.args().toArray());
    }

    /** F6: one event for the public detail page. Empty unless the event is published. */
    public Optional<EventDetails> findPublicById(int id) {
        return jdbc.query(PUBLIC_DETAILS, (row, index) -> toDetails(row), id, PUBLIC_STATUS)
                .stream().findFirst();
    }

    public List<EventCategory> categories() {
        return jdbc.query("SELECT category_id, category_name FROM event_category ORDER BY category_name",
                (row, index) -> new EventCategory(row.getInt("category_id"), row.getString("category_name")));
    }

    record Query(String sql, List<Object> args) {}

    /** Adds one AND condition per filter that was given; every value is a ? parameter. */
    static Query searchQuery(EventFilter filter) {
        StringBuilder sql = new StringBuilder(SEARCH);
        List<Object> args = new ArrayList<>();
        args.add(PUBLIC_STATUS);
        if (filter.query() != null) {
            sql.append("\nAND (e.title ILIKE ? OR e.description ILIKE ? OR c.category_name ILIKE ?"
                    + " OR e.venue_name ILIKE ? OR e.area ILIKE ?)");
            String pattern = contains(filter.query());
            args.addAll(List.of(pattern, pattern, pattern, pattern, pattern));
        }
        if (filter.categoryId() != null) {
            sql.append("\nAND e.category_id = ?");
            args.add(filter.categoryId());
        }
        if (filter.city() != null) {
            // Dhaka events often store the neighbourhood (Gulshan, Dhanmondi) as the area,
            // but their address still contains "Dhaka".
            sql.append("\nAND (e.area ILIKE ? OR e.venue_address ILIKE ?)");
            args.add(escapeLike(filter.city()));
            args.add(contains(filter.city()));
        }
        if (filter.from() != null) {
            sql.append("\nAND e.end_time >= ?");
            args.add(filter.from().atStartOfDay());
        }
        if (filter.until() != null) {
            sql.append("\nAND e.start_time < ?");
            args.add(filter.until().plusDays(1).atStartOfDay());
        }
        if ("free".equals(filter.price())) sql.append("\nAND COALESCE(e.base_price, 0) = 0");
        if ("paid".equals(filter.price())) sql.append("\nAND e.base_price > 0");
        sql.append("\nORDER BY e.start_time, e.event_id\nLIMIT ").append(MAX_RESULTS);
        return new Query(sql.toString(), args);
    }

    static EventSummary toSummary(ResultSet row) throws SQLException {
        return new EventSummary(row.getInt("event_id"), row.getString("title"), row.getString("category_name"),
                row.getString("area"), row.getString("venue_name"),
                row.getObject("start_time", LocalDateTime.class), row.getObject("end_time", LocalDateTime.class),
                row.getBigDecimal("base_price"), row.getString("poster_url"), row.getBoolean("is_verified"));
    }

    static EventDetails toDetails(ResultSet row) throws SQLException {
        return new EventDetails(row.getInt("event_id"), row.getString("title"), row.getString("description"),
                row.getString("category_name"), row.getString("area"), row.getString("venue_name"),
                row.getString("venue_address"), row.getObject("start_time", LocalDateTime.class),
                row.getObject("end_time", LocalDateTime.class), row.getBigDecimal("base_price"),
                row.getString("poster_url"), row.getString("ticket_url"),
                row.getBoolean("is_verified"), row.getString("organizer_name"));
    }

    /** Typed % and _ are matched literally instead of acting as wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String contains(String text) {
        return "%" + escapeLike(text) + "%";
    }
}

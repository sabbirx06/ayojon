package com.ayojon.model;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EventRepository {
    private final JdbcTemplate jdbc;

    public EventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
}
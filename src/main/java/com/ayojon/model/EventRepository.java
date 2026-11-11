package com.ayojon.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
    static final int MAX_RESULTS = 100;

    // Event times are stored as Dhaka local time, so "now" must be Dhaka time too.
    static final String SEARCH = """
            SELECT e.event_id, e.title, c.category_name, e.area, e.venue_name, e.start_time, e.end_time,
                   e.base_price, e.poster_url, e.is_verified
            FROM event e JOIN event_category c ON c.category_id = e.category_id
            WHERE e.status = 'PUBLISHED' AND e.end_time >= (now() AT TIME ZONE 'Asia/Dhaka')""";

    static final String DETAILS = """
            SELECT e.event_id, e.title, e.description, c.category_name, e.area, e.venue_name, e.venue_address,
                   e.start_time, e.end_time, e.base_price, e.poster_url, e.ticket_url, e.status, e.is_verified,
                   u.name AS organizer_name
            FROM event e
            JOIN event_category c ON c.category_id = e.category_id
            JOIN users u ON u.user_id = e.organizer_id
            WHERE e.event_id = ? AND e.status <> 'DRAFT'""";

    private final JdbcTemplate jdbc;

    public EventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Event> search(EventFilter filter) {
        Query query = searchQuery(filter);
        return jdbc.query(query.sql(), (row, index) -> toEvent(row), query.args().toArray());
    }

    public Optional<EventDetails> findById(int id) {
        return jdbc.query(DETAILS, (row, index) -> toDetails(row), id).stream().findFirst();
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

    static Event toEvent(ResultSet row) throws SQLException {
        return new Event(row.getInt("event_id"), row.getString("title"), row.getString("category_name"),
                row.getString("area"), row.getString("venue_name"),
                row.getObject("start_time", LocalDateTime.class), row.getObject("end_time", LocalDateTime.class),
                row.getBigDecimal("base_price"), row.getString("poster_url"), row.getBoolean("is_verified"));
    }

    static EventDetails toDetails(ResultSet row) throws SQLException {
        return new EventDetails(row.getInt("event_id"), row.getString("title"), row.getString("description"),
                row.getString("category_name"), row.getString("area"), row.getString("venue_name"),
                row.getString("venue_address"), row.getObject("start_time", LocalDateTime.class),
                row.getObject("end_time", LocalDateTime.class), row.getBigDecimal("base_price"),
                row.getString("poster_url"), row.getString("ticket_url"), row.getString("status"),
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

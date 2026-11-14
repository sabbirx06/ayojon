package com.ayojon.model;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminReviewRepository {
    private final JdbcTemplate jdbc;

    public AdminReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(int eventId, int adminId, String decision, String note) {
        jdbc.update("INSERT INTO event_verification(event_id, admin_id, decision, source_url) VALUES(?,?,?,?)",
                eventId, adminId, decision, note);
    }

    public List<AdminReview> findByEventId(int eventId) {
        return jdbc.query(
                "SELECT ev.verification_id, ev.event_id, ev.admin_id, u.name AS admin_name, " +
                        "ev.decision, ev.source_url AS note, ev.verified_at " +
                        "FROM event_verification ev " +
                        "JOIN users u ON ev.admin_id = u.user_id " +
                        "WHERE ev.event_id = ? ORDER BY ev.verified_at DESC",
                (row, index) -> new AdminReview(
                        row.getInt("verification_id"),
                        row.getInt("event_id"),
                        row.getInt("admin_id"),
                        row.getString("admin_name"),
                        row.getString("decision"),
                        row.getString("note"),
                        row.getString("verified_at")),
                eventId);
    }
}

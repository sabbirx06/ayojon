package com.ayojon.model;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DisputeRepository {
    private final JdbcTemplate jdbc;

    public DisputeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DisputeReport> findAll() {
        return jdbc.query("""
                SELECT dr.report_id, dr.reporter_id, u.name AS reporter_name,
                       dr.event_id, e.title AS event_title,
                       COALESCE(dreas.reason_name, 'Other') AS reason, dr.details,
                       dr.status, dr.created_at
                FROM dispute_report dr
                LEFT JOIN users u ON dr.reporter_id = u.user_id
                LEFT JOIN event e ON dr.event_id = e.event_id
                LEFT JOIN dispute_reason dreas ON dr.reason_id = dreas.reason_id
                ORDER BY dr.created_at DESC
                """, (row, idx) -> new DisputeReport(
                row.getInt("report_id"),
                row.getInt("reporter_id"),
                row.getString("reporter_name"),
                row.getInt("event_id"),
                row.getString("event_title"),
                row.getString("reason"),
                row.getString("details"),
                row.getString("status"),
                row.getString("created_at")));
    }

    public int countPending() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dispute_report WHERE status IN ('PENDING', 'UNDER_REVIEW')", Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void updateStatus(int reportId, String status) {
        jdbc.update("UPDATE dispute_report SET status = ? WHERE report_id = ?", status, reportId);
    }
}

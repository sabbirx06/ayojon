package com.ayojon.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceRepository {
    private static final RowMapper<Attendance> ROW_MAPPER = (row, index) -> new Attendance(
            row.getInt("attendance_id"), row.getInt("user_id"), row.getInt("event_id"),
            row.getObject("checked_in_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public AttendanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Attendance> recordCheckIn(int userId, int eventId) {
        try {
            return jdbc.query("INSERT INTO attendance(user_id, event_id) VALUES (?, ?) "
                            + "RETURNING attendance_id, user_id, event_id, checked_in_at",
                    ROW_MAPPER, userId, eventId).stream().findFirst();
        } catch (DuplicateKeyException exception) {
            return Optional.empty();
        }
    }

    public List<Attendance> findByUserId(int userId) {
        return jdbc.query("SELECT attendance_id, user_id, event_id, checked_in_at FROM attendance "
                        + "WHERE user_id = ? ORDER BY checked_in_at DESC",
                ROW_MAPPER, userId);
    }

    public boolean isUserCheckedIn(int userId, int eventId) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM attendance WHERE user_id = ? AND event_id = ?)",
                Boolean.class, userId, eventId);
    }

    public int countByEventId(int eventId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attendance WHERE event_id = ?", Integer.class, eventId);
    }
}

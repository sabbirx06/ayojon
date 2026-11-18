package com.ayojon.model;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BadgeRepository {
    private static final RowMapper<Badge> BADGE_ROW_MAPPER = (row, index) -> new Badge(
            row.getInt("badge_id"),
            row.getString("badge_name"),
            row.getInt("milestone_points"),
            row.getString("description"));

    private final JdbcTemplate jdbc;

    public BadgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Badge> findAllBadges() {
        return jdbc.query("SELECT badge_id, badge_name, milestone_points, description FROM badge ORDER BY milestone_points ASC",
                BADGE_ROW_MAPPER);
    }

    public List<Badge> findBadgesByUserId(int userId) {
        return jdbc.query("SELECT b.badge_id, b.badge_name, b.milestone_points, b.description "
                        + "FROM badge b JOIN user_badge ub ON ub.badge_id = b.badge_id "
                        + "WHERE ub.user_id = ? ORDER BY ub.earned_at DESC",
                BADGE_ROW_MAPPER, userId);
    }

    public List<LeaderboardEntry> getLeaderboard(int limit) {
        return jdbc.query("SELECT u.user_id, u.name, COALESCE(u.points, 0) as points, COUNT(a.attendance_id) as check_in_count "
                        + "FROM users u LEFT JOIN attendance a ON a.user_id = u.user_id "
                        + "WHERE u.role = 'USER' "
                        + "GROUP BY u.user_id, u.name, u.points "
                        + "ORDER BY points DESC, check_in_count DESC, u.user_id ASC LIMIT ?",
                (row, index) -> new LeaderboardEntry(
                        row.getInt("user_id"),
                        row.getString("name"),
                        row.getInt("points"),
                        row.getInt("check_in_count")),
                limit);
    }

    public void awardAttendancePointsAndBadges(int userId, int attendanceId) {
        jdbc.update("UPDATE users SET points = COALESCE(points, 0) + 50 WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO user_badge (user_id, badge_id, attendance_id) "
                + "SELECT ?, b.badge_id, ? FROM badge b JOIN users u ON u.user_id = ? "
                + "WHERE u.points >= b.milestone_points "
                + "ON CONFLICT (user_id, badge_id) DO NOTHING", userId, attendanceId, userId);
    }
}

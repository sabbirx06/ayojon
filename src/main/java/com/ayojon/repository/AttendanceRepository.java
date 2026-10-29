package com.ayojon.repository;

import com.ayojon.entity.Attendance;
import com.ayojon.repository.projection.AttendanceSummary;
import com.ayojon.repository.projection.HeatmapPoint;
import com.ayojon.repository.projection.MonthlyAttendance;
import com.ayojon.repository.projection.NamedCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    /** F20 participation history, newest first. */
    List<Attendance> findByUserIdOrderByCheckedInAtDesc(Long userId);

    long countByEventId(Long eventId);

    /** F2 heat-map points for one user. */
    @Query(value = """
            SELECT venue_id AS venueId, venue_name AS venueName, area_name AS areaName,
                   latitude, longitude, visit_count AS visitCount
              FROM v_user_heatmap WHERE user_id = :userId
            """, nativeQuery = true)
    List<HeatmapPoint> heatmap(@Param("userId") Long userId);

    /** F20 dashboard headline numbers. */
    @Query(value = """
            SELECT user_id AS userId, points, total_events_attended AS totalEventsAttended,
                   first_check_in AS firstCheckIn, last_check_in AS lastCheckIn,
                   favorite_category AS favoriteCategory, most_visited_area AS mostVisitedArea,
                   badges_earned AS badgesEarned
              FROM v_user_attendance_summary WHERE user_id = :userId
            """, nativeQuery = true)
    AttendanceSummary summary(@Param("userId") Long userId);

    /** F20 monthly attendance trend, oldest month first. */
    @Query(value = """
            SELECT month, events_attended AS eventsAttended, points_earned AS pointsEarned
              FROM v_user_monthly_attendance WHERE user_id = :userId ORDER BY month
            """, nativeQuery = true)
    List<MonthlyAttendance> monthlyTrend(@Param("userId") Long userId);

    /** F20 favourite categories. */
    @Query(value = """
            SELECT category_id AS id, category_name AS name, events_attended AS eventsAttended
              FROM v_user_category_stats WHERE user_id = :userId
             ORDER BY events_attended DESC, last_check_in DESC
            """, nativeQuery = true)
    List<NamedCount> categoryBreakdown(@Param("userId") Long userId);

    /** F20 most visited areas. */
    @Query(value = """
            SELECT area_id AS id, area_name AS name, events_attended AS eventsAttended
              FROM v_user_area_stats WHERE user_id = :userId
             ORDER BY events_attended DESC, last_check_in DESC
            """, nativeQuery = true)
    List<NamedCount> areaBreakdown(@Param("userId") Long userId);
}

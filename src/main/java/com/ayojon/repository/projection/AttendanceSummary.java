package com.ayojon.repository.projection;

import java.time.Instant;

/** F20 dashboard headline numbers. */
public interface AttendanceSummary {
    Long getUserId();
    Integer getPoints();
    Long getTotalEventsAttended();
    Instant getFirstCheckIn();
    Instant getLastCheckIn();
    String getFavoriteCategory();
    String getMostVisitedArea();
    Long getBadgesEarned();
}

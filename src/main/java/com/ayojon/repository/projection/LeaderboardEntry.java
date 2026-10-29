package com.ayojon.repository.projection;

/** F3 leaderboard row. Ties share a rank. */
public interface LeaderboardEntry {
    Long getRank();
    Long getUserId();
    String getName();
    String getAvatarUrl();
    Integer getPoints();
    Long getEventsAttended();
    Long getBadgeCount();
}

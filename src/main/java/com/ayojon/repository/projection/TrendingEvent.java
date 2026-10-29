package com.ayojon.repository.projection;

/** F8: event card plus its rank by bookmarks in the last 7 days. */
public interface TrendingEvent extends EventCard {
    Long getTrendRank();
    Long getWeeklyBookmarks();
}

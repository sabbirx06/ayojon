package com.ayojon.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

/** One event as shown on cards / search results (from {@code v_event_listing}). */
public interface EventCard {
    Long getEventId();
    String getTitle();
    String getStatus();
    Instant getStartTime();
    Instant getEndTime();
    String getCategoryName();
    String getVenueName();
    String getAreaName();
    BigDecimal getLatitude();
    BigDecimal getLongitude();
    /** Cheapest ticket tier; null if the event has no tiers. */
    BigDecimal getMinPrice();
    Boolean getFree();
    Boolean getVerified();
    String getPosterUrl();
    BigDecimal getAvgRating();
    Long getReviewCount();
    Long getBookmarkCount();
}

package com.ayojon.repository.projection;

import java.time.Instant;

/** F18: an event waiting for admin review. */
public interface SubmissionQueueItem {
    Long getEventId();
    String getTitle();
    Long getOrganizerId();
    String getOrganizerName();
    Instant getStartTime();
    Instant getEndTime();
    String getVenueName();
    Instant getCreatedAt();
    Long getPreviousReviews();
    /** Number of other live events at the same venue at the same time (F11). */
    Long getVenueOverlaps();
    String getOfficialSourceUrl();
}

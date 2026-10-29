package com.ayojon.repository.projection;

import java.time.Instant;

/** F18: what an organizer sees for each of their submissions. */
public interface SubmissionStatus {
    Long getEventId();
    String getTitle();
    String getStatus();
    Instant getSubmittedAt();
    Instant getPublishedAt();
    Boolean getVerified();
    String getLastDecision();
    String getLastComment();
    Instant getLastReviewedAt();
}

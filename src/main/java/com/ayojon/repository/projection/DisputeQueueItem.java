package com.ayojon.repository.projection;

import java.time.Instant;

/** F14: an open report in the admin queue. */
public interface DisputeQueueItem {
    Long getReportId();
    String getStatus();
    String getReasonCode();
    String getReasonName();
    String getDetails();
    String getEvidenceUrl();
    Long getEventId();
    String getEventTitle();
    Long getReporterId();
    String getReporterName();
    Instant getCreatedAt();
    Long getOpenReportsForEvent();
}

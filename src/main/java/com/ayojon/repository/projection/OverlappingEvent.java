package com.ayojon.repository.projection;

import java.time.Instant;

/** F11: an existing event whose time range clashes with a proposed slot. */
public interface OverlappingEvent {
    Long getEventId();
    String getTitle();
    Long getVenueId();
    Instant getStartTime();
    Instant getEndTime();
}

package com.ayojon.repository.projection;

import java.time.Instant;

/** F10/F11: one entry of a chronological itinerary. */
public interface ItinerarySlot {
    Long getSequenceNo();
    Long getEventId();
    String getTitle();
    String getStatus();
    String getVenueName();
    Instant getSlotStart();
    Instant getSlotEnd();
    String getNote();
    Boolean getHasConflict();
}

package com.ayojon.repository.projection;

import java.time.Instant;

/** F13: fields for one VEVENT in an .ics file (or one CSV row). Times are UTC. */
public interface CalendarEntry {
    String getUid();
    String getSummary();
    String getDescription();
    Instant getDtstart();
    Instant getDtend();
    String getLocation();
    String getUrl();
    String getIcalStatus();
    Instant getLastModified();
}

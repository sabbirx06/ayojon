package com.ayojon.repository.projection;

import java.time.Instant;

/** F17: one row of an event's public audit trail. */
public interface ChangeLogEntry {
    Long getChangeId();
    String getChangeType();
    String getFieldName();
    String getOldValue();
    String getNewValue();
    String getNote();
    String getChangedByName();
    Instant getChangedAt();
}

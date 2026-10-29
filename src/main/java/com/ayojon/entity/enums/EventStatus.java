package com.ayojon.entity.enums;

/** Lifecycle of an event (event.status). "Completed" is not a status: an event is over when its endTime has passed. */
public enum EventStatus {
    DRAFT, PENDING_REVIEW, REVISION_REQUESTED, PUBLISHED, REJECTED, CANCELLED
}

package com.ayojon.repository.projection;

/** F20 breakdown row: a category or area name and how many events were attended there. */
public interface NamedCount {
    Long getId();
    String getName();
    Long getEventsAttended();
}

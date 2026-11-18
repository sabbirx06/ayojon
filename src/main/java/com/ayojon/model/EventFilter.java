package com.ayojon.model;

import java.time.LocalDate;

/**
 * Discover filters. A null value means "not filtered".
 * price is "free" or "paid"; from and until are calendar days, both inclusive.
 */
public record EventFilter(String query, Integer categoryId, String city,
                          LocalDate from, LocalDate until, String price) {}

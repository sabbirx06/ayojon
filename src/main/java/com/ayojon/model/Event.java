package com.ayojon.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One event card in the Discover search results. Times are Dhaka local time. */
public record Event(int id, String title, String category, String area, String venueName,
                    LocalDateTime startTime, LocalDateTime endTime, BigDecimal price,
                    String posterUrl, boolean verified) {}

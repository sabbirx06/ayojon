package com.ayojon.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Everything the event detail page shows. Times are Dhaka local time. */
public record EventDetails(int id, String title, String description, String category, String area,
                           String venueName, String venueAddress, LocalDateTime startTime, LocalDateTime endTime,
                           BigDecimal price, String posterUrl, String ticketUrl, String status,
                           boolean verified, String organizerName) {}

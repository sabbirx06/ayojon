package com.ayojon.model;

public record DisputeReport(int reportId, int reporterId, String reporterName,
                            int eventId, String eventTitle,
                            String reason, String details,
                            String status, String createdAt) {}

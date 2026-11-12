package com.ayojon.model;

public record Event(int eventId, int organizerId, String organizerName,
                    String categoryName, String venueName, String areaName,
                    String title, String description,
                    String startTime, String endTime,
                    String ticketUrl, String status, String createdAt,
                    String latestDecision, String latestNote) {}

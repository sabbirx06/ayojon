package com.ayojon.model;

public record AdminReview(int verificationId, int eventId, int adminId,
                          String adminName, String decision, String note,
                          String verifiedAt) {}

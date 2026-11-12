package com.ayojon.model;

import java.time.LocalDateTime;

public record Attendance(int id, int userId, int eventId, LocalDateTime checkedInAt) {}

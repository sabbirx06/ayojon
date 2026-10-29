package com.ayojon.repository.projection;

import java.time.LocalDate;

/** F20 monthly trend; {@code month} is the first day of the month (Dhaka time). */
public interface MonthlyAttendance {
    LocalDate getMonth();
    Long getEventsAttended();
    Long getPointsEarned();
}

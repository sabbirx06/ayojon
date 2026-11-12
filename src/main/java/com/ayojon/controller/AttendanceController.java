package com.ayojon.controller;

import com.ayojon.model.Attendance;
import com.ayojon.model.AttendanceRepository;
import com.ayojon.model.UserRepository;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceRepository attendance;
    private final UserRepository users;

    public AttendanceController(AttendanceRepository attendance, UserRepository users) {
        this.attendance = attendance;
        this.users = users;
    }

    public record CheckIn(Integer eventId) {}

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public Attendance checkIn(@RequestBody CheckIn request, Principal principal) {
        validateEventId(request.eventId());
        int userId = currentUserId(principal);
        try {
            return attendance.recordCheckIn(userId, request.eventId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Already checked in."));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The user or event no longer exists.");
        }
    }

    @GetMapping("/me")
    public List<Attendance> me(Principal principal) {
        return attendance.findByUserId(currentUserId(principal));
    }

    @GetMapping("/events/{eventId}/status")
    public Map<String, Boolean> status(@PathVariable int eventId, Principal principal) {
        validateEventId(eventId);
        return Map.of("checkedIn", attendance.isUserCheckedIn(currentUserId(principal), eventId));
    }

    @GetMapping("/events/{eventId}/count")
    public Map<String, Integer> count(@PathVariable int eventId) {
        validateEventId(eventId);
        return Map.of("eventId", eventId, "attendanceCount", attendance.countByEventId(eventId));
    }

    private int currentUserId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."))
                .id();
    }

    private void validateEventId(Integer eventId) {
        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive eventId is required.");
        }
    }
}

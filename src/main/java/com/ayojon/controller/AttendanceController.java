package com.ayojon.controller;

import com.ayojon.model.Attendance;
import com.ayojon.model.AttendanceRepository;
import com.ayojon.model.Badge;
import com.ayojon.model.BadgeRepository;
import com.ayojon.model.LeaderboardEntry;
import com.ayojon.model.UserRepository;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final BadgeRepository badges;

    @org.springframework.beans.factory.annotation.Autowired
    public AttendanceController(AttendanceRepository attendance, UserRepository users, @org.springframework.beans.factory.annotation.Autowired(required = false) BadgeRepository badges) {
        this.attendance = attendance;
        this.users = users;
        this.badges = badges;
    }

    public record CheckIn(Integer eventId) {}

    @Transactional
    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public Attendance checkIn(@RequestBody CheckIn request, Principal principal) {
        validateEventId(request.eventId());
        int userId = currentUserId(principal);
        Attendance record;
        try {
            record = attendance.recordCheckIn(userId, request.eventId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Already checked in."));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The user or event no longer exists.");
        }
        if (badges != null) {
            badges.awardAttendancePointsAndBadges(userId, record.id());
        }
        return record;
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> leaderboard() {
        return badges != null ? badges.getLeaderboard(10) : List.of();
    }

    @GetMapping("/badges")
    public List<Badge> badges() {
        return badges != null ? badges.findAllBadges() : List.of();
    }

    @GetMapping("/badges/me")
    public List<Badge> myBadges(Principal principal) {
        int userId = currentUserId(principal);
        return badges != null ? badges.findBadgesByUserId(userId) : List.of();
    }

    @GetMapping("/events")
    public List<AttendanceRepository.EventChoice> events(Principal principal) {
        currentUserId(principal);
        return attendance.findPublishedEvents();
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

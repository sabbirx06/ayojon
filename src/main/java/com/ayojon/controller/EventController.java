package com.ayojon.controller;

import com.ayojon.model.Event;
import com.ayojon.model.EventRepository;
import com.ayojon.model.UserRepository;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventRepository events;
    private final UserRepository users;

    public EventController(EventRepository events, UserRepository users) {
        this.events = events;
        this.users = users;
    }

    public record EventSubmission(String title, String description, String startTime, String endTime,
                                  String venueName, String venueAddress, String areaName,
                                  String categoryName, String ticketUrl,
                                  Double latitude, Double longitude,
                                  String posterUrl) {}   // ADDED (poster)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@RequestBody EventSubmission s, Principal principal) {
        var organizer = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        validate(s);
        Timestamp start = parseTime(s.startTime());
        Timestamp end = parseTime(s.endTime());
        if (!end.after(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time.");
        }
        events.create(organizer.id(), s.categoryName().trim(), s.venueName().trim(),
                s.venueAddress().trim(), s.areaName().trim(),
                s.latitude(), s.longitude(),
                s.title().trim(), s.description() == null ? "" : s.description().trim(),
                start, end, s.ticketUrl() == null || s.ticketUrl().isBlank() ? null : s.ticketUrl().trim(),
                posterOrNull(s.posterUrl()));   // ADDED (poster)
        return Map.of("message", "Event submitted for review.");
    }

    @GetMapping("/mine")
    public List<Event> mine(Principal principal) {
        var organizer = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return events.findByOrganizerId(organizer.id());
    }

    @GetMapping("/{id}")
    public Event detail(@PathVariable int id) {
        return events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    @PutMapping("/{id}")
    public Map<String, String> update(@PathVariable int id, @RequestBody EventSubmission s, Principal principal) {
        var organizer = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var existing = events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
        if (existing.organizerId() != organizer.id()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own events.");
        }
        if (!"DRAFT".equals(existing.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only draft events can be edited.");
        }
        validate(s);
        Timestamp start = parseTime(s.startTime());
        Timestamp end = parseTime(s.endTime());
        if (!end.after(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time.");
        }
        events.update(id, organizer.id(), s.categoryName().trim(), s.venueName().trim(),
                s.venueAddress().trim(), s.areaName().trim(),
                s.latitude(), s.longitude(),
                s.title().trim(), s.description() == null ? "" : s.description().trim(),
                start, end, s.ticketUrl() == null || s.ticketUrl().isBlank() ? null : s.ticketUrl().trim(),
                posterOrNull(s.posterUrl()));   // ADDED (poster)
        return Map.of("message", "Event updated and resubmitted for review.");
    }

    private void validate(EventSubmission s) {
        if (s.title() == null || s.title().isBlank() || s.title().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event title is required (max 255 characters).");
        }
        if (s.startTime() == null || s.endTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start and end times are required.");
        }
        if (s.venueName() == null || s.venueName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue name is required.");
        }
        if (s.venueAddress() == null || s.venueAddress().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue address is required.");
        }
        if (s.areaName() == null || s.areaName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Area is required.");
        }
        if (s.categoryName() == null || s.categoryName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is required.");
        }
        if (s.latitude() == null || s.longitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coordinates are required.");
        }
        // ADDED (poster): the poster link is optional, but if present it must be an http(s) URL
        if (s.posterUrl() != null && !s.posterUrl().isBlank()
                && (!s.posterUrl().trim().matches("^https?://\\S+$") || s.posterUrl().length() > 2000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The poster link must be a valid http(s) URL.");
        }
    }

    // ADDED (poster)
    private String posterOrNull(String url) {
        return url == null || url.isBlank() ? null : url.trim();
    }

    private Timestamp parseTime(String value) {
        try {
            return Timestamp.valueOf(LocalDateTime.parse(value));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date/time format.");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? "Request could not be completed." : ex.getReason()));
    }
}
package com.ayojon.controller;

import com.ayojon.model.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final EventRepository events;
    private final AdminReviewRepository reviews;
    private final UserRepository users;
    private final DisputeRepository disputes;

    public AdminController(EventRepository events, AdminReviewRepository reviews,
                           UserRepository users, DisputeRepository disputes) {
        this.events = events;
        this.reviews = reviews;
        this.users = users;
        this.disputes = disputes;
    }

    public record ReviewDecision(String decision, String note) {}
    public record DisputeStatusUpdate(String status) {}

    @GetMapping("/stats")
    public AdminStats stats() {
        return new AdminStats(
                events.countPending(),
                events.count(),
                users.count(),
                disputes.countPending());
    }

    @GetMapping("/events")
    public List<Event> allEvents() {
        return events.findAll();
    }

    @GetMapping("/events/pending")
    public List<Event> pendingEvents() {
        return events.findPendingReview();
    }

    @GetMapping("/events/{id}")
    public Event eventDetail(@PathVariable int id) {
        return events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    @PostMapping("/events/{id}/review")
    public Map<String, String> reviewEvent(@PathVariable int id, @RequestBody ReviewDecision decision,
                                          Principal principal) {
        var admin = users.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var event = events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));

        if (!"DRAFT".equals(event.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending events can be reviewed.");
        }

        String d = decision.decision();
        if (d == null || d.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision is required.");
        }

        String note = decision.note() == null ? null : decision.note().trim();
        if (note != null && note.isEmpty()) note = null;

        return switch (d) {
            case "APPROVED" -> {
                events.updateStatus(id, "PUBLISHED");
                reviews.create(id, admin.id(), "APPROVED", note);
                yield Map.of("message", "Event approved and published.");
            }
            case "REJECTED" -> {
                if (note == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note is required when rejecting.");
                }
                events.updateStatus(id, "CANCELLED");
                reviews.create(id, admin.id(), "REJECTED", note);
                yield Map.of("message", "Event rejected.");
            }
            case "REVISION_REQUESTED" -> {
                if (note == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note is required when requesting revisions.");
                }
                reviews.create(id, admin.id(), "REJECTED", "[REVISION] " + note);
                yield Map.of("message", "Revision requested. The organizer will see your feedback.");
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Decision must be APPROVED, REJECTED, or REVISION_REQUESTED.");
        };
    }

    @GetMapping("/events/{id}/reviews")
    public List<AdminReview> reviewHistory(@PathVariable int id) {
        events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
        return reviews.findByEventId(id);
    }

    @GetMapping("/users")
    public List<User> allUsers() {
        return users.findAll();
    }

    @GetMapping("/disputes")
    public List<DisputeReport> allDisputes() {
        return disputes.findAll();
    }

    @PostMapping("/disputes/{id}/status")
    public Map<String, String> updateDisputeStatus(@PathVariable int id, @RequestBody DisputeStatusUpdate update) {
        if (!List.of("UNDER_REVIEW", "RESOLVED", "DISMISSED").contains(update.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dispute status.");
        }
        disputes.updateStatus(id, update.status());
        return Map.of("message", "Dispute report marked as " + update.status());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? "Request could not be completed." : ex.getReason()));
    }
}

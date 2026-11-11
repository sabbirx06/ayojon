package com.ayojon.controller;

import com.ayojon.model.Event;
import com.ayojon.model.EventCategory;
import com.ayojon.model.EventDetails;
import com.ayojon.model.EventFilter;
import com.ayojon.model.EventRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/* F5 search & filtering and F6 event details. Like every /api URL except auth, these need a login. */
@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventRepository events;

    public EventController(EventRepository events) {
        this.events = events;
    }

    @GetMapping
    public List<Event> search(@RequestParam(required = false) String q,
                              @RequestParam(required = false) Integer categoryId,
                              @RequestParam(required = false) String city,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until,
                              @RequestParam(required = false) String price) {
        String query = blankToNull(q);
        String priceFilter = blankToNull(price);
        if (query != null && query.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text can be at most 100 characters.");
        }
        if (priceFilter != null && !List.of("free", "paid").contains(priceFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be free or paid.");
        }
        if (from != null && until != null && from.isAfter(until)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The From date must be on or before the Until date.");
        }
        return events.search(new EventFilter(query, categoryId, blankToNull(city), from, until, priceFilter));
    }

    @GetMapping("/categories")
    public List<EventCategory> categories() {
        return events.categories();
    }

    @GetMapping("/{id:\\d+}")
    public EventDetails details(@PathVariable int id) {
        return events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This event could not be found."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason() == null ? "Request could not be completed." : exception.getReason()));
    }

    /** e.g. from=tomorrow or categoryId=music */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> invalidValue(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "The value for " + exception.getName() + " is not valid."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

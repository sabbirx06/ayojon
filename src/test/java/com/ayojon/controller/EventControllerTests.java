package com.ayojon.controller;

import com.ayojon.config.SecurityConfig;
import com.ayojon.model.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({EventController.class, AuthController.class})
@Import({SecurityConfig.class, EventControllerTests.TestSecurity.class})
class EventControllerTests {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {}

    @Autowired MockMvc mvc;
    @MockitoBean EventRepository events;
    @MockitoBean UserRepository users;

    // ---------------------------------------------------------------- discovery (F5, F6)

    @Test
    void eventsRequireLogin() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events/3")).andExpect(status().isUnauthorized());
        verifyNoInteractions(events);
    }

    @Test
    void searchPassesEveryFilterToTheModel() throws Exception {
        when(events.search(any())).thenReturn(List.of(summary()));
        mvc.perform(loggedIn(get("/api/events").param("q", "  street food ").param("categoryId", "4")
                        .param("city", "Dhaka").param("from", "2026-11-20").param("until", "2026-11-22").param("price", "paid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].title").value("Gulshan Street Food Carnival"))
                .andExpect(jsonPath("$[0].startTime").value("2026-11-20T15:00:00"))
                .andExpect(jsonPath("$[0].price").value(200.0))
                .andExpect(jsonPath("$[0].posterUrl").value("https://example.com/poster.jpg"))
                .andExpect(jsonPath("$[0].verified").value(true));
        verify(events).search(new EventFilter("street food", 4, "Dhaka",
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22), "paid"));
    }

    @Test
    void blankFiltersMeanNoFilter() throws Exception {
        mvc.perform(loggedIn(get("/api/events").param("q", "   ").param("city", "").param("price", "")))
                .andExpect(status().isOk());
        verify(events).search(new EventFilter(null, null, null, null, null, null));
    }

    @Test
    void invalidFiltersGetAReadableMessage() throws Exception {
        mvc.perform(loggedIn(get("/api/events").param("price", "cheap")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Price must be free or paid."));
        mvc.perform(loggedIn(get("/api/events").param("from", "2026-12-10").param("until", "2026-12-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The From date must be on or before the Until date."));
        mvc.perform(loggedIn(get("/api/events").param("q", "a".repeat(101))))
                .andExpect(status().isBadRequest());
        mvc.perform(loggedIn(get("/api/events").param("from", "tomorrow")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("The value for from is not valid."));
        verifyNoInteractions(events);
    }

    @Test
    void categoriesComeFromTheModel() throws Exception {
        when(events.categories()).thenReturn(List.of(new EventCategory(1, "Concerts & Music")));
        mvc.perform(loggedIn(get("/api/events/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Concerts & Music"));
    }

    @Test
    void detailsShowTheWholePublishedEvent() throws Exception {
        when(events.findPublicById(3)).thenReturn(Optional.of(new EventDetails(3, "Gulshan Street Food Carnival",
                "Over 40 street food stalls.", "Food & Festivals", "Gulshan", "Gulshan Youth Club Grounds",
                "Road 113, Gulshan-2, Dhaka 1212", LocalDateTime.of(2026, 11, 20, 15, 0), LocalDateTime.of(2026, 11, 21, 23, 0),
                new BigDecimal("200.00"), "https://example.com/poster.jpg", "https://example.com/tickets",
                true, "Dhaka Events Lab")));
        mvc.perform(loggedIn(get("/api/events/3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Gulshan Street Food Carnival"))
                .andExpect(jsonPath("$.organizerName").value("Dhaka Events Lab"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.posterUrl").value("https://example.com/poster.jpg"))
                .andExpect(jsonPath("$.description").value("Over 40 street food stalls."))
                .andExpect(jsonPath("$.endTime").value("2026-11-21T23:00:00"));
    }

    @Test
    void unknownEventReturnsNotFound() throws Exception {
        when(events.findPublicById(99)).thenReturn(Optional.empty());
        mvc.perform(loggedIn(get("/api/events/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This event could not be found."));
    }

    @Test
    void draftOrRejectedEventIsNotVisibleThroughPublicDetails() throws Exception {
        // The privileged lookup would find it (any status), but the public page must not use it.
        when(events.findById(7)).thenReturn(Optional.of(organizerEvent("CANCELLED", "REJECTED", null)));
        when(events.findPublicById(7)).thenReturn(Optional.empty());
        mvc.perform(loggedIn(get("/api/events/7")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This event could not be found."));
        verify(events, never()).findById(anyInt());
    }

    // ---------------------------------------------------------------- organizer

    @Test
    void unauthenticatedUserCannotSubmitEvents() throws Exception {
        mvc.perform(post("/api/events")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCanSubmitEvent() throws Exception {
        when(users.findByEmail("org@example.com")).thenReturn(Optional.of(
                new User(10, "Organizer", "org@example.com", "ORGANIZER", 0, "hash")));

        var csrf = csrf();
        String body = """
                {
                    "title": "Summer Fest",
                    "startTime": "2026-11-20T18:00:00",
                    "endTime": "2026-11-20T22:00:00",
                    "venueName": "Banani Park",
                    "venueAddress": "Road 11, Banani",
                    "areaName": "Dhaka",
                    "categoryName": "Music",
                    "latitude": 23.7937,
                    "longitude": 90.4066,
                    "description": "Fun festival",
                    "posterUrl": "https://example.com/summer.jpg"
                }
                """;

        mvc.perform(post("/api/events")
                .session(csrf.session())
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("org@example.com", "ROLE_ORGANIZER"))
                .header(csrf.header(), csrf.token())
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Event submitted for review."));

        verify(events).create(eq(10), eq("Music"), eq("Banani Park"), eq("Road 11, Banani"),
                eq("Dhaka"), eq(23.7937), eq(90.4066), eq("Summer Fest"),
                eq("Fun festival"), any(Timestamp.class), any(Timestamp.class), isNull(),
                eq("https://example.com/summer.jpg"));
    }

    @Test
    void organizerCanListOwnSubmissionsInEveryStatus() throws Exception {
        when(users.findByEmail("org@example.com")).thenReturn(Optional.of(
                new User(10, "Organizer", "org@example.com", "ORGANIZER", 0, "hash")));
        when(events.findByOrganizerId(10)).thenReturn(List.of(
                organizerEvent("DRAFT", null, null), organizerEvent("CANCELLED", "REJECTED", "Duplicate")));

        mvc.perform(get("/api/events/mine")
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("org@example.com", "ROLE_ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Summer Fest"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));
    }

    @Test
    void organizerCanUpdateDraftEvent() throws Exception {
        when(users.findByEmail("org@example.com")).thenReturn(Optional.of(
                new User(10, "Organizer", "org@example.com", "ORGANIZER", 0, "hash")));
        when(events.findById(1)).thenReturn(Optional.of(
                organizerEvent("DRAFT", "REJECTED", "[REVISION] Fix description")));

        mvc.perform(organizerPut("/api/events/1", updateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event updated and resubmitted for review."));

        verify(events).update(eq(1), eq(10), eq("Music"), eq("Banani Park"), eq("Road 11, Banani"),
                eq("Dhaka"), eq(23.7937), eq(90.4066), eq("Summer Fest Revised"),
                eq("Updated fun festival"), any(Timestamp.class), any(Timestamp.class), isNull(), isNull());
    }

    @Test
    void editAfterAdminReviewedReturnsConflict() throws Exception {
        when(users.findByEmail("org@example.com")).thenReturn(Optional.of(
                new User(10, "Organizer", "org@example.com", "ORGANIZER", 0, "hash")));
        when(events.findById(1)).thenReturn(Optional.of(organizerEvent("DRAFT", null, null)));
        doThrow(new EventNotEditableException(1)).when(events).update(anyInt(), anyInt(), any(), any(), any(), any(),
                anyDouble(), anyDouble(), any(), any(), any(), any(), any(), any());

        mvc.perform(organizerPut("/api/events/1", updateBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This event was already reviewed and can no longer be edited."));
    }

    // ---------------------------------------------------------------- helpers

    private EventSummary summary() {
        return new EventSummary(3, "Gulshan Street Food Carnival", "Food & Festivals", "Gulshan", "Gulshan Youth Club Grounds",
                LocalDateTime.of(2026, 11, 20, 15, 0), LocalDateTime.of(2026, 11, 21, 23, 0),
                new BigDecimal("200.00"), "https://example.com/poster.jpg", true);
    }

    private Event organizerEvent(String status, String decision, String note) {
        return new Event(1, 10, "Organizer", "Music", "Banani Park", "Dhaka",
                "Summer Fest", "Fun festival", "2026-11-20T18:00:00", "2026-11-20T22:00:00",
                null, status, "2026-11-12T10:00:00", decision, note, null);
    }

    private String updateBody() {
        return """
                {
                    "title": "Summer Fest Revised",
                    "startTime": "2026-11-20T18:00:00",
                    "endTime": "2026-11-20T22:00:00",
                    "venueName": "Banani Park",
                    "venueAddress": "Road 11, Banani",
                    "areaName": "Dhaka",
                    "categoryName": "Music",
                    "latitude": 23.7937,
                    "longitude": 90.4066,
                    "description": "Updated fun festival"
                }
                """;
    }

    private MockHttpServletRequestBuilder organizerPut(String url, String body) throws Exception {
        var csrf = csrf();
        return put(url)
                .session(csrf.session())
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("org@example.com", "ROLE_ORGANIZER"))
                .header(csrf.header(), csrf.token())
                .contentType("application/json")
                .content(body);
    }

    private MockHttpServletRequestBuilder loggedIn(MockHttpServletRequestBuilder request) {
        return request.sessionAttr("SPRING_SECURITY_CONTEXT",
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated("user@example.com", "", List.of())));
    }

    private SecurityContextImpl authContext(String email, String role) {
        return new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                email, "", List.of(new SimpleGrantedAuthority(role))));
    }

    private Csrf csrf() throws Exception {
        var session = new MockHttpSession();
        var response = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode body = JsonMapper.builder().build().readTree(response.getResponse().getContentAsString());
        return new Csrf(session, body.get("headerName").stringValue(), body.get("token").stringValue());
    }

    private record Csrf(MockHttpSession session, String header, String token) {}
}

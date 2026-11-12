package com.ayojon.controller;

import com.ayojon.config.SecurityConfig;
import com.ayojon.model.*;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminController.class, AuthController.class})
@Import({SecurityConfig.class, AdminControllerTests.TestSecurity.class})
class AdminControllerTests {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {}

    @Autowired MockMvc mvc;
    @MockitoBean EventRepository events;
    @MockitoBean AdminReviewRepository reviews;
    @MockitoBean UserRepository users;
    @MockitoBean DisputeRepository disputes;

    @Test
    void unauthenticatedUserCannotAccessPendingEvents() throws Exception {
        mvc.perform(get("/api/admin/events/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminCannotAccessPendingEvents() throws Exception {
        mvc.perform(get("/api/admin/events/pending")
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("user@example.com", "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanFetchPendingEvents() throws Exception {
        var sampleEvent = new Event(1, 10, "Organizer A", "Music", "Rooftop Banani", "Dhaka",
                "Concert", "Description", "2026-11-20T18:00:00", "2026-11-20T22:00:00",
                null, "DRAFT", "2026-11-12T10:00:00", null, null);
        when(events.findPendingReview()).thenReturn(List.of(sampleEvent));

        mvc.perform(get("/api/admin/events/pending")
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("admin@example.com", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(1))
                .andExpect(jsonPath("$[0].title").value("Concert"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void adminCanFetchAllEvents() throws Exception {
        var sampleEvent = new Event(1, 10, "Organizer A", "Music", "Rooftop Banani", "Dhaka",
                "Concert", "Description", "2026-11-20T18:00:00", "2026-11-20T22:00:00",
                null, "PUBLISHED", "2026-11-12T10:00:00", "APPROVED", null);
        when(events.findAll()).thenReturn(List.of(sampleEvent));

        mvc.perform(get("/api/admin/events")
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("admin@example.com", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(1))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void adminCanFetchDashboardStats() throws Exception {
        when(events.countPending()).thenReturn(3);
        when(events.count()).thenReturn(10);
        when(users.count()).thenReturn(25);
        when(disputes.countPending()).thenReturn(1);

        mvc.perform(get("/api/admin/stats")
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("admin@example.com", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingApprovals").value(3))
                .andExpect(jsonPath("$.totalEvents").value(10))
                .andExpect(jsonPath("$.totalUsers").value(25))
                .andExpect(jsonPath("$.pendingDisputes").value(1));
    }

    @Test
    void adminCanApprovePendingEvent() throws Exception {
        var sampleEvent = new Event(1, 10, "Organizer A", "Music", "Rooftop Banani", "Dhaka",
                "Concert", "Description", "2026-11-20T18:00:00", "2026-11-20T22:00:00",
                null, "DRAFT", "2026-11-12T10:00:00", null, null);
        when(events.findById(1)).thenReturn(Optional.of(sampleEvent));
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(
                new User(99, "Admin User", "admin@example.com", "ADMIN", 0, "hash")));

        var csrf = csrf();
        String body = "{\"decision\":\"APPROVED\",\"note\":\"Looks good\"}";

        mvc.perform(post("/api/admin/events/1/review")
                .session(csrf.session())
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("admin@example.com", "ROLE_ADMIN"))
                .header(csrf.header(), csrf.token())
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event approved and published."));

        verify(events).updateStatus(1, "PUBLISHED");
        verify(reviews).create(1, 99, "APPROVED", "Looks good");
    }

    @Test
    void adminCanRequestRevision() throws Exception {
        var sampleEvent = new Event(1, 10, "Organizer A", "Music", "Rooftop Banani", "Dhaka",
                "Concert", "Description", "2026-11-20T18:00:00", "2026-11-20T22:00:00",
                null, "DRAFT", "2026-11-12T10:00:00", null, null);
        when(events.findById(1)).thenReturn(Optional.of(sampleEvent));
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(
                new User(99, "Admin User", "admin@example.com", "ADMIN", 0, "hash")));

        var csrf = csrf();
        String body = "{\"decision\":\"REVISION_REQUESTED\",\"note\":\"Please update schedule\"}";

        mvc.perform(post("/api/admin/events/1/review")
                .session(csrf.session())
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("admin@example.com", "ROLE_ADMIN"))
                .header(csrf.header(), csrf.token())
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Revision requested. The organizer will see your feedback."));

        verify(reviews).create(1, 99, "REJECTED", "[REVISION] Please update schedule");
    }

    @Test
    void adminCanRejectEvent() throws Exception {
        var sampleEvent = new Event(1, 10, "Organizer A", "Music", "Rooftop Banani", "Dhaka",
                "Concert", "Description", "2026-11-20T18:00:00", "2026-11-20T22:00:00",
                null, "DRAFT", "2026-11-12T10:00:00", null, null);
        when(events.findById(1)).thenReturn(Optional.of(sampleEvent));
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(
                new User(99, "Admin User", "admin@example.com", "ADMIN", 0, "hash")));

        var csrf = csrf();
        String body = "{\"decision\":\"REJECTED\",\"note\":\"Spam listing\"}";

        mvc.perform(post("/api/admin/events/1/review")
                .session(csrf.session())
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("admin@example.com", "ROLE_ADMIN"))
                .header(csrf.header(), csrf.token())
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event rejected."));

        verify(events).updateStatus(1, "CANCELLED");
        verify(reviews).create(1, 99, "REJECTED", "Spam listing");
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

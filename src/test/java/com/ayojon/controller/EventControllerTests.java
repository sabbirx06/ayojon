package com.ayojon.controller;

import com.ayojon.config.SecurityConfig;
import com.ayojon.model.*;
import java.sql.Timestamp;
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

@WebMvcTest({EventController.class, AuthController.class})
@Import({SecurityConfig.class, EventControllerTests.TestSecurity.class})
class EventControllerTests {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {}

    @Autowired MockMvc mvc;
    @MockitoBean EventRepository events;
    @MockitoBean UserRepository users;

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
                    "description": "Fun festival"
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
                eq("Fun festival"), any(Timestamp.class), any(Timestamp.class), isNull());
    }

    @Test
    void organizerCanListOwnSubmissions() throws Exception {
        when(users.findByEmail("org@example.com")).thenReturn(Optional.of(
                new User(10, "Organizer", "org@example.com", "ORGANIZER", 0, "hash")));

        var sampleEvent = new Event(1, 10, "Organizer", "Music", "Banani Park", "Dhaka",
                "Summer Fest", "Fun festival", "2026-11-20T18:00:00", "2026-11-20T22:00:00",
                null, "DRAFT", "2026-11-12T10:00:00", null, null);
        when(events.findByOrganizerId(10)).thenReturn(List.of(sampleEvent));

        mvc.perform(get("/api/events/mine")
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("org@example.com", "ROLE_ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Summer Fest"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void organizerCanUpdateDraftEvent() throws Exception {
        when(users.findByEmail("org@example.com")).thenReturn(Optional.of(
                new User(10, "Organizer", "org@example.com", "ORGANIZER", 0, "hash")));

        var sampleEvent = new Event(1, 10, "Organizer", "Music", "Banani Park", "Dhaka",
                "Summer Fest", "Fun festival", "2026-11-20T18:00:00", "2026-11-20T22:00:00",
                null, "DRAFT", "2026-11-12T10:00:00", "REJECTED", "[REVISION] Fix description");
        when(events.findById(1)).thenReturn(Optional.of(sampleEvent));

        var csrf = csrf();
        String body = """
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

        mvc.perform(put("/api/events/1")
                .session(csrf.session())
                .sessionAttr("SPRING_SECURITY_CONTEXT", authContext("org@example.com", "ROLE_ORGANIZER"))
                .header(csrf.header(), csrf.token())
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event updated and resubmitted for review."));

        verify(events).update(eq(1), eq(10), eq("Music"), eq("Banani Park"), eq("Road 11, Banani"),
                eq("Dhaka"), eq(23.7937), eq(90.4066), eq("Summer Fest Revised"),
                eq("Updated fun festival"), any(Timestamp.class), any(Timestamp.class), isNull());
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

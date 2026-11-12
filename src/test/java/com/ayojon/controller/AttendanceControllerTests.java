package com.ayojon.controller;

import com.ayojon.config.SecurityConfig;
import com.ayojon.model.Attendance;
import com.ayojon.model.AttendanceRepository;
import com.ayojon.model.User;
import com.ayojon.model.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AttendanceController.class, AuthController.class})
@Import({SecurityConfig.class, AttendanceControllerTests.TestSecurity.class})
class AttendanceControllerTests {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {}

    @Autowired MockMvc mvc;
    @MockitoBean AttendanceRepository attendance;
    @MockitoBean UserRepository users;

    @Test
    void anonymousRequestsCannotReadPrivateAttendanceOrCheckIn() throws Exception {
        mvc.perform(get("/api/attendance/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attendance/events/12/status")).andExpect(status().isUnauthorized());
        var csrf = csrf(new MockHttpSession());
        mvc.perform(post("/api/attendance/check-in").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType("application/json").content("{\"eventId\":12}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(attendance, users);
    }

    @Test
    void publicCountDoesNotRequireOrResolveAnAccount() throws Exception {
        when(attendance.countByEventId(12)).thenReturn(4);
        mvc.perform(get("/api/attendance/events/12/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(12))
                .andExpect(jsonPath("$.attendanceCount").value(4));
        verifyNoInteractions(users);
    }

    @Test
    void publicCountReturnsZeroForAnEventWithoutAttendance() throws Exception {
        mvc.perform(get("/api/attendance/events/12/count"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendanceCount").value(0));
    }

    @Test
    void checkInUsesSessionIdentityAndReturnsCreatedAttendance() throws Exception {
        when(attendance.recordCheckIn(7, 12)).thenReturn(Optional.of(checkIn()));
        postCheckIn("{\"eventId\":12,\"userId\":99}", authenticatedSession())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.eventId").value(12))
                .andExpect(jsonPath("$.checkedInAt").value("2026-11-12T10:30:00"));
        verify(attendance).recordCheckIn(7, 12);
    }

    @Test
    void duplicateCheckInReturnsConflict() throws Exception {
        when(attendance.recordCheckIn(7, 12)).thenReturn(Optional.empty());
        postCheckIn("{\"eventId\":12}", authenticatedSession()).andExpect(status().isConflict());
    }

    @Test
    void deletedEventReturnsBadRequestInsteadOfServerError() throws Exception {
        when(attendance.recordCheckIn(7, 12)).thenThrow(new DataIntegrityViolationException("missing event"));
        postCheckIn("{\"eventId\":12}", authenticatedSession()).andExpect(status().isBadRequest());
    }

    @Test
    void checkInRequiresCsrfEvenWithAnAuthenticatedSession() throws Exception {
        mvc.perform(post("/api/attendance/check-in").session(authenticatedSession())
                        .contentType("application/json").content("{\"eventId\":12}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(attendance);
    }

    @Test
    void invalidCheckInBodiesNeverReachAttendanceRepository() throws Exception {
        for (String body : List.of("{}", "{\"eventId\":null}", "{\"eventId\":0}", "{\"eventId\":-1}",
                "{\"eventId\":\"invalid\"}", "{\"eventId\":2147483648}", "null", "{")) {
            postCheckIn(body, authenticatedSession()).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(attendance);
    }

    @Test
    void historyContainsOnlyAttendanceForTheSessionUser() throws Exception {
        when(attendance.findByUserId(7)).thenReturn(List.of(checkIn()));
        mvc.perform(get("/api/attendance/me").session(authenticatedSession()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(7))
                .andExpect(jsonPath("$[0].eventId").value(12));
        verify(attendance).findByUserId(7);
    }

    @Test
    void historyWithoutCheckInsReturnsEmptyArray() throws Exception {
        mvc.perform(get("/api/attendance/me").session(authenticatedSession()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void statusReflectsTheSessionUserAndSelectedEvent() throws Exception {
        when(attendance.isUserCheckedIn(7, 12)).thenReturn(true);
        var session = authenticatedSession();
        mvc.perform(get("/api/attendance/events/12/status").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkedIn").value(true));
        mvc.perform(get("/api/attendance/events/13/status").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkedIn").value(false));
        verify(attendance).isUserCheckedIn(7, 12);
        verify(attendance).isUserCheckedIn(7, 13);
    }

    @Test
    void staleSessionCannotReadOrWriteAttendance() throws Exception {
        var session = authenticatedSession();
        when(users.findByEmail("user@example.com")).thenReturn(Optional.empty());
        mvc.perform(get("/api/attendance/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attendance/events/12/status").session(session)).andExpect(status().isUnauthorized());
        postCheckIn("{\"eventId\":12}", session).andExpect(status().isUnauthorized());
        verifyNoInteractions(attendance);
    }

    @Test
    void invalidPathIdsDoNotQueryAttendance() throws Exception {
        var session = authenticatedSession();
        for (String id : List.of("0", "-1", "invalid", "2147483648")) {
            mvc.perform(get("/api/attendance/events/" + id + "/count")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/attendance/events/" + id + "/status").session(session))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(attendance);
    }

    private Attendance checkIn() {
        return new Attendance(3, 7, 12, LocalDateTime.of(2026, 11, 12, 10, 30));
    }

    private MockHttpSession authenticatedSession() {
        when(users.findByEmail("user@example.com"))
                .thenReturn(Optional.of(new User(7, "Test User", "user@example.com", "USER", 0, "stored-hash")));
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated("user@example.com", "", List.of())));
        return session;
    }

    private org.springframework.test.web.servlet.ResultActions postCheckIn(String body, MockHttpSession session) throws Exception {
        var csrf = csrf(session);
        return mvc.perform(post("/api/attendance/check-in").session(session).header(csrf.header(), csrf.token())
                .contentType("application/json").content(body));
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var body = JsonMapper.builder().build().readTree(response.getResponse().getContentAsString());
        return new Csrf(session, body.get("headerName").stringValue(), body.get("token").stringValue());
    }

    private record Csrf(MockHttpSession session, String header, String token) {}
}

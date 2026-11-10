package com.ayojon.controller;

import com.ayojon.config.SecurityConfig;
import com.ayojon.model.User;
import com.ayojon.model.UserRepository;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthControllerTests.TestSecurity.class})
class AuthControllerTests {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {}

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean UserRepository users;

    @Test
    void anonymousSessionDoesNotQueryDatabase() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
        verifyNoInteractions(users);
    }

    @Test
    void sessionExposesProfileWithoutPasswordHash() throws Exception {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
        mvc.perform(get("/api/auth/me").sessionAttr("SPRING_SECURITY_CONTEXT", context("user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value(7))
                .andExpect(jsonPath("$.user.name").value("Test User"))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.points").value(12))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void missingAccountReturnsAnonymousSession() throws Exception {
        when(users.findByEmail("deleted@example.com")).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/me").sessionAttr("SPRING_SECURITY_CONTEXT", context("deleted@example.com")))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void signupNormalizesIdentityHashesPasswordAndRestrictsRole() throws Exception {
        signup("  Test User  ", "  USER@Example.com  ", "password123", "ADMIN", 201);
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(users).create(eq("Test User"), eq("user@example.com"), hash.capture(), eq("USER"));
        assertTrue(passwords.matches("password123", hash.getValue()));
    }

    @Test
    void signupAllowsOrganizerRole() throws Exception {
        signup("Test User", "user@example.com", "password123", "organizer", 201);
        verify(users).create(eq("Test User"), eq("user@example.com"), anyString(), eq("ORGANIZER"));
    }

    @Test
    void invalidSignupNeverWritesToDatabase() throws Exception {
        signup(" ", "user@example.com", "password123", "user", 400);
        signup("Test", "invalid", "password123", "user", 400);
        signup("Test", "user@example.com", "short", "user", 400);
        signup("Test", "user@example.com", "a".repeat(73), "user", 400);
        signup("Test", "user@example.com", "\u00e9".repeat(37), "user", 400);
        verifyNoInteractions(users);
    }

    @Test
    void duplicateSignupKeepsConflictMessage() throws Exception {
        doThrow(new DuplicateKeyException("duplicate")).when(users).create(anyString(), anyString(), anyString(), anyString());
        signup("Test", "user@example.com", "password123", "user", 409);
    }

    @Test
    void signupRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void loginUsesModelAndLogoutClearsSession() throws Exception {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
        var csrf = csrf();
        var session = csrf.session();
        mvc.perform(post("/api/auth/login").session(session).header(csrf.header(), csrf.token())
                        .param("email", " USER@Example.com ").param("password", "password123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.user.email").value("user@example.com"));
        var refreshed = csrf(session);
        mvc.perform(post("/api/auth/logout").session(session).header(refreshed.header(), refreshed.token()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
    }

    @Test
    void invalidLoginKeepsUnauthorizedResponse() throws Exception {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user()));
        var csrf = csrf();
        mvc.perform(post("/api/auth/login").session(csrf.session()).header(csrf.header(), csrf.token())
                        .param("email", "user@example.com").param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    private User user() {
        return new User(7, "Test User", "user@example.com", "USER", 12, passwords.encode("password123"));
    }

    private SecurityContextImpl context(String email) {
        return new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(email, "", List.of()));
    }

    private void signup(String name, String email, String password, String role, int status) throws Exception {
        var csrf = csrf();
        String body = JsonMapper.builder().build().writeValueAsString(new AuthController.Signup(name, email, password, role));
        mvc.perform(post("/api/auth/signup").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType("application/json").content(body))
                .andExpect(status().is(status)).andExpect(jsonPath("$.message").isString());
    }

    private Csrf csrf() throws Exception {
        return csrf(new MockHttpSession());
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode body = JsonMapper.builder().build().readTree(response.getResponse().getContentAsString());
        return new Csrf(session, body.get("headerName").stringValue(), body.get("token").stringValue());
    }

    private record Csrf(MockHttpSession session, String header, String token) {}
}

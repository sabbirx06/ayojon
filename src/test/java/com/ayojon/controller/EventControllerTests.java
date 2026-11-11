package com.ayojon.controller;

import com.ayojon.config.SecurityConfig;
import com.ayojon.model.Event;
import com.ayojon.model.EventCategory;
import com.ayojon.model.EventDetails;
import com.ayojon.model.EventFilter;
import com.ayojon.model.EventRepository;
import com.ayojon.model.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, EventControllerTests.TestSecurity.class})
class EventControllerTests {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {}

    @Autowired MockMvc mvc;
    @MockitoBean EventRepository events;
    @MockitoBean UserRepository users;

    @Test
    void eventsRequireLogin() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events/3")).andExpect(status().isUnauthorized());
        verifyNoInteractions(events);
    }

    @Test
    void searchPassesEveryFilterToTheModel() throws Exception {
        when(events.search(any())).thenReturn(List.of(event()));
        mvc.perform(loggedIn(get("/api/events").param("q", "  street food ").param("categoryId", "4")
                        .param("city", "Dhaka").param("from", "2026-11-20").param("until", "2026-11-22").param("price", "paid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].title").value("Gulshan Street Food Carnival"))
                .andExpect(jsonPath("$[0].startTime").value("2026-11-20T15:00:00"))
                .andExpect(jsonPath("$[0].price").value(200.0))
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
    void detailsShowTheWholeEvent() throws Exception {
        when(events.findById(3)).thenReturn(Optional.of(new EventDetails(3, "Gulshan Street Food Carnival",
                "Over 40 street food stalls.", "Food & Festivals", "Gulshan", "Gulshan Youth Club Grounds",
                "Road 113, Gulshan-2, Dhaka 1212", LocalDateTime.of(2026, 11, 20, 15, 0), LocalDateTime.of(2026, 11, 21, 23, 0),
                new BigDecimal("200.00"), "https://example.com/poster.jpg", "https://example.com/tickets",
                "PUBLISHED", true, "Dhaka Events Lab")));
        mvc.perform(loggedIn(get("/api/events/3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Gulshan Street Food Carnival"))
                .andExpect(jsonPath("$.organizerName").value("Dhaka Events Lab"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.description").value("Over 40 street food stalls."))
                .andExpect(jsonPath("$.endTime").value("2026-11-21T23:00:00"));
    }

    @Test
    void unknownEventReturnsNotFound() throws Exception {
        when(events.findById(99)).thenReturn(Optional.empty());
        mvc.perform(loggedIn(get("/api/events/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This event could not be found."));
    }

    private Event event() {
        return new Event(3, "Gulshan Street Food Carnival", "Food & Festivals", "Gulshan", "Gulshan Youth Club Grounds",
                LocalDateTime.of(2026, 11, 20, 15, 0), LocalDateTime.of(2026, 11, 21, 23, 0),
                new BigDecimal("200.00"), "https://example.com/poster.jpg", true);
    }

    private MockHttpServletRequestBuilder loggedIn(MockHttpServletRequestBuilder request) {
        return request.sessionAttr("SPRING_SECURITY_CONTEXT",
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated("user@example.com", "", List.of())));
    }
}

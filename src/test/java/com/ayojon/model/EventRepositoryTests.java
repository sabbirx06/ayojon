package com.ayojon.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventRepositoryTests {
    @Test
    void searchWithoutFiltersOnlyShowsUpcomingPublishedEvents() {
        var query = EventRepository.searchQuery(new EventFilter(null, null, null, null, null, null));
        assertEquals(EventRepository.SEARCH + "\nORDER BY e.start_time, e.event_id\nLIMIT 100", query.sql());
        assertTrue(query.args().isEmpty());
    }

    @Test
    void everyFilterAddsOneConditionWithBoundValues() {
        var query = EventRepository.searchQuery(new EventFilter("jazz", 3, "Dhaka",
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22), "paid"));
        assertTrue(query.sql().contains("AND (e.title ILIKE ? OR e.description ILIKE ? OR c.category_name ILIKE ? OR e.venue_name ILIKE ? OR e.area ILIKE ?)"));
        assertTrue(query.sql().contains("AND e.category_id = ?"));
        assertTrue(query.sql().contains("AND (e.area ILIKE ? OR e.venue_address ILIKE ?)"));
        assertTrue(query.sql().contains("AND e.end_time >= ?"));
        assertTrue(query.sql().contains("AND e.start_time < ?"));
        assertTrue(query.sql().contains("AND e.base_price > 0"));
        assertFalse(query.sql().contains("jazz"), "values must never be pasted into the SQL");
        assertEquals(List.of("%jazz%", "%jazz%", "%jazz%", "%jazz%", "%jazz%", 3, "Dhaka", "%Dhaka%",
                LocalDateTime.of(2026, 11, 20, 0, 0), LocalDateTime.of(2026, 11, 23, 0, 0)), query.args());
    }

    @Test
    void freeFilterTreatsMissingPriceAsFree() {
        var query = EventRepository.searchQuery(new EventFilter(null, null, null, null, null, "free"));
        assertTrue(query.sql().contains("AND COALESCE(e.base_price, 0) = 0"));
        assertFalse(query.sql().contains("e.base_price > 0"));
    }

    @Test
    void wildcardsTypedByUserAreMatchedLiterally() {
        var query = EventRepository.searchQuery(new EventFilter("100%_off", null, null, null, null, null));
        assertEquals("%100\\%\\_off%", query.args().getFirst());
    }

    @Test
    void detailsMapEveryColumn() throws Exception {
        var row = mock(ResultSet.class);
        var start = LocalDateTime.of(2026, 11, 20, 15, 0);
        var end = LocalDateTime.of(2026, 11, 21, 23, 0);
        when(row.getInt("event_id")).thenReturn(3);
        when(row.getString("title")).thenReturn("Gulshan Street Food Carnival");
        when(row.getString("description")).thenReturn("Over 40 street food stalls.");
        when(row.getString("category_name")).thenReturn("Food & Festivals");
        when(row.getString("area")).thenReturn("Gulshan");
        when(row.getString("venue_name")).thenReturn("Gulshan Youth Club Grounds");
        when(row.getString("venue_address")).thenReturn("Road 113, Gulshan-2, Dhaka 1212");
        when(row.getObject("start_time", LocalDateTime.class)).thenReturn(start);
        when(row.getObject("end_time", LocalDateTime.class)).thenReturn(end);
        when(row.getBigDecimal("base_price")).thenReturn(new BigDecimal("200.00"));
        when(row.getString("poster_url")).thenReturn("https://example.com/poster.jpg");
        when(row.getString("ticket_url")).thenReturn("https://example.com/tickets");
        when(row.getString("status")).thenReturn("PUBLISHED");
        when(row.getBoolean("is_verified")).thenReturn(true);
        when(row.getString("organizer_name")).thenReturn("Dhaka Events Lab");

        assertEquals(new EventDetails(3, "Gulshan Street Food Carnival", "Over 40 street food stalls.",
                "Food & Festivals", "Gulshan", "Gulshan Youth Club Grounds", "Road 113, Gulshan-2, Dhaka 1212",
                start, end, new BigDecimal("200.00"), "https://example.com/poster.jpg",
                "https://example.com/tickets", "PUBLISHED", true, "Dhaka Events Lab"), EventRepository.toDetails(row));
    }

    @Test
    void findByIdBindsTheIdAndHidesDrafts() {
        var jdbc = mock(JdbcTemplate.class);
        assertTrue(new EventRepository(jdbc).findById(5).isEmpty());
        verify(jdbc).query(eq(EventRepository.DETAILS), org.mockito.ArgumentMatchers.<RowMapper<EventDetails>>any(), eq(5));
        assertTrue(EventRepository.DETAILS.contains("e.status <> 'DRAFT'"));
    }
}

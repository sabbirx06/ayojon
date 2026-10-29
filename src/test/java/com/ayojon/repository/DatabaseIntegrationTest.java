package com.ayojon.repository;

import com.ayojon.entity.Attendance;
import com.ayojon.entity.Event;
import com.ayojon.entity.EventSubmissionReview;
import com.ayojon.entity.User;
import com.ayojon.entity.enums.EventStatus;
import com.ayojon.entity.enums.Role;
import com.ayojon.entity.enums.SubmissionDecision;
import com.ayojon.repository.projection.EventCard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs every custom repository query against the real database (migrations +
 * demo seed) and walks one write flow through the triggers. Each test rolls
 * back, so the database is left unchanged.
 */
@SpringBootTest
@Transactional
class DatabaseIntegrationTest {

    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired AttendanceRepository attendance;
    @Autowired ItineraryRepository itineraries;
    @Autowired ItineraryItemRepository itineraryItems;
    @Autowired EventChangeLogRepository changeLog;
    @Autowired EventSubmissionReviewRepository submissionReviews;
    @Autowired DisputeReportRepository disputes;
    @Autowired NotificationRepository notifications;
    @Autowired VoucherRepository vouchers;
    @Autowired AreaRepository areas;
    @Autowired EventCategoryRepository categories;
    @Autowired VenueRepository venues;
    @Autowired EntityManager em;

    private User user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    @Test
    void searchNearbyTrendingAndRecommendations() {
        List<EventCard> all = events.search(null, null, null, null, null, null, null);
        assertFalse(all.isEmpty());
        assertNotNull(all.get(0).getEventId());
        assertNotNull(all.get(0).getStartTime());

        List<EventCard> freeMusic = events.search("music", OffsetDateTime.now(), null, null, null, null, BigDecimal.ZERO);
        assertTrue(freeMusic.stream().allMatch(e -> e.getMinPrice().signum() == 0));

        Long dhanmondi = areas.findAllByOrderByAreaNameAsc().stream()
                .filter(a -> a.getAreaName().equals("Dhanmondi")).findFirst().orElseThrow().getId();
        assertTrue(events.search(null, null, null, dhanmondi, null, null, null).stream()
                .allMatch(e -> e.getAreaName().equals("Dhanmondi")));

        assertFalse(events.findNearby(23.75, 90.39, 3).isEmpty());
        assertFalse(events.findTrending(10).isEmpty());
        assertFalse(events.recommendFor(user("nabila@ayojon.dev").getId(), 5).isEmpty());
    }

    @Test
    void leaderboardAndDashboard() {
        var board = users.leaderboard(10);
        assertEquals("Rafi Hasan", board.get(0).getName());
        assertEquals(50, board.get(0).getPoints());

        Long rafi = user("rafi@ayojon.dev").getId();
        var summary = attendance.summary(rafi);
        assertEquals(4L, summary.getTotalEventsAttended());
        assertNotNull(summary.getFavoriteCategory());
        assertFalse(attendance.monthlyTrend(rafi).isEmpty());
        assertFalse(attendance.categoryBreakdown(rafi).isEmpty());
        assertFalse(attendance.areaBreakdown(rafi).isEmpty());
        assertEquals(4, attendance.heatmap(rafi).size());
    }

    @Test
    void itineraryConflictsAndCalendarExport() {
        Long rafi = user("rafi@ayojon.dev").getId();
        Long plan = itineraries.findByUserIdOrderByStartDateDesc(rafi).get(0).getId();

        var schedule = itineraryItems.schedule(plan);
        assertEquals(4, schedule.size());
        assertTrue(schedule.stream().anyMatch(s -> Boolean.TRUE.equals(s.getHasConflict())));
        assertEquals(4, itineraryItems.calendarExport(plan).size());
    }

    @Test
    void adminQueuesAndVouchers() {
        assertEquals(2, submissionReviews.pendingQueue().size());
        assertFalse(submissionReviews.statusForOrganizer(user("techhub@ayojon.dev").getId()).isEmpty());
        assertEquals(2, disputes.openQueue().size());
        assertFalse(vouchers.findClaimable().isEmpty());

        Event pitch = events.findByStatusOrderByStartTimeAsc(EventStatus.PUBLISHED).stream()
                .filter(e -> e.getTitle().startsWith("Startup Pitch")).findFirst().orElseThrow();
        assertFalse(changeLog.publicLog(pitch.getId()).isEmpty());
        assertTrue(pitch.isVerified());
    }

    @Test
    void submitApproveAndCheckInFlowRunsThroughTriggers() {
        User organizer = user("techhub@ayojon.dev");
        User admin = user("admin@ayojon.dev");
        User attendee = user("arif@ayojon.dev");
        assertEquals(Role.ORGANIZER, organizer.getRole());

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setCategory(categories.findByCategoryNameIgnoreCase("technology").orElseThrow());
        event.setVenue(venues.findByVenueNameContainingIgnoreCase("BICC").get(0));
        event.setTitle("Integration Test Meetup");
        event.setStartTime(OffsetDateTime.now().minusHours(1));
        event.setEndTime(OffsetDateTime.now().plusHours(2));
        events.saveAndFlush(event);
        assertEquals(EventStatus.PENDING_REVIEW, event.getStatus());

        assertTrue(events.findOverlapping(event.getStartTime(), event.getEndTime(), event.getVenue().getId(), event.getId())
                .stream().noneMatch(o -> o.getEventId().equals(event.getId())));

        EventSubmissionReview review = new EventSubmissionReview();
        review.setEvent(event);
        review.setAdmin(admin);
        review.setDecision(SubmissionDecision.APPROVED);
        submissionReviews.saveAndFlush(review);

        em.refresh(event);
        assertEquals(EventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());

        Attendance checkIn = new Attendance();
        checkIn.setUser(attendee);
        checkIn.setEvent(event);
        attendance.saveAndFlush(checkIn);
        assertEquals(0, checkIn.getPointsAwarded());   // not verified -> no points
        assertTrue(attendance.existsByUserIdAndEventId(attendee.getId(), event.getId()));

        assertTrue(notifications.countByUserIdAndReadFalse(organizer.getId()) > 0);
        assertTrue(notifications.markAllRead(organizer.getId()) > 0);
    }
}

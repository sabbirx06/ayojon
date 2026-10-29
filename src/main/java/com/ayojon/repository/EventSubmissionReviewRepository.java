package com.ayojon.repository;

import com.ayojon.entity.EventSubmissionReview;
import com.ayojon.repository.projection.SubmissionQueueItem;
import com.ayojon.repository.projection.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventSubmissionReviewRepository extends JpaRepository<EventSubmissionReview, Long> {

    List<EventSubmissionReview> findByEventIdOrderByReviewedAtDesc(Long eventId);

    /** F18 admin queue, oldest submission first. */
    @Query(value = """
            SELECT event_id AS eventId, title, organizer_id AS organizerId, organizer_name AS organizerName,
                   start_time AS startTime, end_time AS endTime, venue_name AS venueName, created_at AS createdAt,
                   previous_reviews AS previousReviews, venue_overlaps AS venueOverlaps,
                   official_source_url AS officialSourceUrl
              FROM v_admin_submission_queue ORDER BY created_at
            """, nativeQuery = true)
    List<SubmissionQueueItem> pendingQueue();

    /** F18 organizer's status page. */
    @Query(value = """
            SELECT event_id AS eventId, title, status, submitted_at AS submittedAt, published_at AS publishedAt,
                   is_verified AS verified, last_decision AS lastDecision, last_comment AS lastComment,
                   last_reviewed_at AS lastReviewedAt
              FROM v_organizer_submission_status WHERE organizer_id = :organizerId
             ORDER BY submitted_at DESC
            """, nativeQuery = true)
    List<SubmissionStatus> statusForOrganizer(@Param("organizerId") Long organizerId);
}

package com.ayojon.entity;

import com.ayojon.entity.enums.SubmissionDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/**
 * Admin decision on a PENDING_REVIEW event (F18). Saving one moves the
 * event's status (APPROVED → PUBLISHED) and notifies the organizer.
 * REJECTED and REVISION_REQUESTED require a comment.
 */
@Entity
@Table(name = "event_submission_review")
@Getter
@Setter
@NoArgsConstructor
public class EventSubmissionReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubmissionDecision decision;

    private String comment;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime reviewedAt;
}

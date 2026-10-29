package com.ayojon.entity;

import com.ayojon.entity.enums.EventStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * An event listing (F16). Things the database does for you:
 * <ul>
 *   <li>inserting an {@link EventSubmissionReview} changes {@code status} (F18);</li>
 *   <li>every UPDATE is written to {@code event_change_log} and followers are
 *       notified (F17, F12) — set {@link #updatedBy} in the same save so the
 *       audit trail records who made the change;</li>
 *   <li>{@code publishedAt} is stamped when the status first becomes PUBLISHED.</li>
 * </ul>
 * {@link #verified} is computed from the latest {@link EventVerification} (F7).
 */
@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private EventCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private OffsetDateTime startTime;

    @Column(nullable = false)
    private OffsetDateTime endTime;

    private String ticketUrl;

    private String officialSourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status = EventStatus.PENDING_REVIEW;

    /** Required when status is CANCELLED. */
    private String cancellationReason;

    @Column(nullable = false)
    private int checkInPoints = 10;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(insertable = false, updatable = false)
    private OffsetDateTime publishedAt;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** Who made the latest edit; feeds the audit trail. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Formula("event_is_verified(event_id)")
    private boolean verified;

    @ManyToMany
    @JoinTable(name = "event_tag",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();
}

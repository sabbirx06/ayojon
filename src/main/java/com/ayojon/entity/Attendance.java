package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/**
 * A check-in (F3, F2, F20). Only PUBLISHED events accept check-ins.
 * {@code pointsAwarded} is set by the database (event's points if verified,
 * else 0), which also updates {@link User#getPoints()} and grants badges.
 */
@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime checkedInAt;

    @Generated
    @Column(insertable = false, updatable = false)
    private Integer pointsAwarded;
}

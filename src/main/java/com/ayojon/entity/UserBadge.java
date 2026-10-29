package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/** A badge a user has unlocked; granted automatically by the database (F3). */
@Entity
@Table(name = "user_badge")
@Getter
@Setter
@NoArgsConstructor
public class UserBadge {

    @EmbeddedId
    private UserBadgeId id = new UserBadgeId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("badgeId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id")
    private Badge badge;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime earnedAt;
}

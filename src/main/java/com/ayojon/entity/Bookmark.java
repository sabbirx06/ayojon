package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/** A saved event (F8 trending, F9 collections, F19 recommendations). */
@Entity
@Table(name = "bookmark")
@Getter
@Setter
@NoArgsConstructor
public class Bookmark {

    @EmbeddedId
    private UserEventId id = new UserEventId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime bookmarkedAt;

    public Bookmark(User user, Event event) {
        this.user = user;
        this.event = event;
    }
}

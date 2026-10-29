package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/** Follow an event to be notified when it changes (F12). */
@Entity
@Table(name = "event_follow")
@Getter
@Setter
@NoArgsConstructor
public class EventFollow {

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
    private OffsetDateTime followedAt;

    public EventFollow(User user, Event event) {
        this.user = user;
        this.event = event;
    }
}

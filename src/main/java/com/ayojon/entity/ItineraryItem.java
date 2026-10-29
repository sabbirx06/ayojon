package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/**
 * An event in a weekend plan (F10). The event must be PUBLISHED and fall
 * within the itinerary's dates. {@code plannedStart/plannedEnd} are optional
 * and must lie inside the event's own times; read the effective schedule and
 * conflicts from the {@code v_itinerary_schedule} view (F11).
 */
@Entity
@Table(name = "itinerary_item")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryItem {

    @EmbeddedId
    private ItineraryItemId id = new ItineraryItemId();

    @MapsId("itineraryId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id")
    private Itinerary itinerary;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    private OffsetDateTime plannedStart;

    private OffsetDateTime plannedEnd;

    private String note;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime addedAt;

    public ItineraryItem(Itinerary itinerary, Event event) {
        this.itinerary = itinerary;
        this.event = event;
    }
}

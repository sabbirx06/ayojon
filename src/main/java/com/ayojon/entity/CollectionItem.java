package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/**
 * An event inside a collection (F9). The database fills {@code userId}
 * from the collection and bookmarks the event if it wasn't already.
 */
@Entity
@Table(name = "collection_item")
@Getter
@Setter
@NoArgsConstructor
public class CollectionItem {

    @EmbeddedId
    private CollectionItemId id = new CollectionItemId();

    @MapsId("collectionId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(insertable = false, updatable = false)
    private Long userId;

    private String note;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime addedAt;

    public CollectionItem(Collection collection, Event event) {
        this.collection = collection;
        this.event = event;
    }
}

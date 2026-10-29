package com.ayojon.entity;

import com.ayojon.entity.enums.CollectionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;

/** A named group of a user's saved events, or a private wishlist (F9). */
@Entity
@Table(name = "collection")
@Getter
@Setter
@NoArgsConstructor
public class Collection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Unique per user. */
    @Column(nullable = false, length = 150)
    private String collectionName;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollectionType collectionType = CollectionType.COLLECTION;

    @Column(name = "is_private", nullable = false)
    private boolean privateCollection;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}

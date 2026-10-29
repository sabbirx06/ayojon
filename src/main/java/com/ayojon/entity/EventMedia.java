package com.ayojon.entity;

import com.ayojon.entity.enums.MediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

@Entity
@Table(name = "event_media")
@Getter
@Setter
@NoArgsConstructor
public class EventMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType mediaType = MediaType.IMAGE;

    @Column(nullable = false)
    private int sortOrder;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime uploadedAt;
}

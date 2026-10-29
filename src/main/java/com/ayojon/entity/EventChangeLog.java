package com.ayojon.entity;

import com.ayojon.entity.enums.ChangeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/**
 * Audit trail row (F17). Written automatically by the database on every
 * event update; the app only inserts {@code NOTE} rows by hand.
 */
@Entity
@Table(name = "event_change_log")
@Getter
@Setter
@NoArgsConstructor
public class EventChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "change_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChangeType changeType;

    @Column(length = 50)
    private String fieldName;

    private String oldValue;

    private String newValue;

    private String note;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime changedAt;
}

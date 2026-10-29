package com.ayojon.entity;

import com.ayojon.entity.enums.VerificationDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/** Admin legitimacy check (F7). VERIFIED requires a sourceUrl. */
@Entity
@Table(name = "event_verification")
@Getter
@Setter
@NoArgsConstructor
public class EventVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationDecision decision;

    private String sourceUrl;

    private String notes;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime verifiedAt;
}

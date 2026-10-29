package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Promotional discount code (F4). Claims are validated by the database:
 * active, inside the validity window, in stock, and the user has at least
 * {@code minPointsRequired} points.
 */
@Entity
@Table(name = "voucher")
@Getter
@Setter
@NoArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private AdCampaign campaign;

    /** Optional: limit the voucher to one event's bookings. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    /** Must be upper-case. */
    @Column(nullable = false, unique = true, length = 50)
    private String promoCode;

    private String description;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(nullable = false)
    private int minPointsRequired;

    /** null = unlimited. */
    private Integer totalQuantity;

    private String bookingUrl;

    @Column(nullable = false)
    private OffsetDateTime validFrom = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime validUntil;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}

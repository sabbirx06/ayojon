package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/** A sponsor's ad campaign; vouchers are generated from it (F4). */
@Entity
@Table(name = "ad_campaign")
@Getter
@Setter
@NoArgsConstructor
public class AdCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "campaign_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sponsor_id", nullable = false)
    private Sponsor sponsor;

    @Column(nullable = false, length = 150)
    private String campaignName;

    private String description;

    private String bannerUrl;

    /** Third-party booking site. */
    private String targetUrl;

    @Column(nullable = false)
    private OffsetDateTime startsAt;

    @Column(nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}

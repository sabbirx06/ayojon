package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sponsor")
@Getter
@Setter
@NoArgsConstructor
public class Sponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sponsor_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String sponsorName;

    private String websiteUrl;

    private String logoUrl;

    private String contactEmail;

    @Generated
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}

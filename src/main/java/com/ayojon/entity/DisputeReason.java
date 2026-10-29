package com.ayojon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dispute_reason")
@Getter
@Setter
@NoArgsConstructor
public class DisputeReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reason_id")
    private Long id;

    /** Stable code such as SPAM or COUNTERFEIT_LISTING. */
    @Column(nullable = false, unique = true, length = 40)
    private String reasonCode;

    @Column(nullable = false, unique = true, length = 100)
    private String reasonName;
}

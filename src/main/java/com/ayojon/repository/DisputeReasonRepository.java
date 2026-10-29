package com.ayojon.repository;

import com.ayojon.entity.DisputeReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisputeReasonRepository extends JpaRepository<DisputeReason, Long> {

    Optional<DisputeReason> findByReasonCode(String reasonCode);
}

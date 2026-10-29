package com.ayojon.repository;

import com.ayojon.entity.EventVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventVerificationRepository extends JpaRepository<EventVerification, Long> {

    List<EventVerification> findByEventIdOrderByVerifiedAtDesc(Long eventId);
}

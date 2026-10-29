package com.ayojon.repository;

import com.ayojon.entity.TicketTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketTierRepository extends JpaRepository<TicketTier, Long> {

    List<TicketTier> findByEventIdOrderByPriceAsc(Long eventId);
}

package com.ayojon.repository;

import com.ayojon.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    List<Itinerary> findByUserIdOrderByStartDateDesc(Long userId);
}

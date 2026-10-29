package com.ayojon.repository;

import com.ayojon.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    List<Venue> findByAreaIdOrderByVenueNameAsc(Long areaId);

    List<Venue> findByVenueNameContainingIgnoreCase(String query);
}

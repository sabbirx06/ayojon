package com.ayojon.repository;

import com.ayojon.entity.EventMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventMediaRepository extends JpaRepository<EventMedia, Long> {

    List<EventMedia> findByEventIdOrderBySortOrderAsc(Long eventId);
}

package com.ayojon.repository;

import com.ayojon.entity.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventCategoryRepository extends JpaRepository<EventCategory, Long> {

    Optional<EventCategory> findByCategoryNameIgnoreCase(String categoryName);
}

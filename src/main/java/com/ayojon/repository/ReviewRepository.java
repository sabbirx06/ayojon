package com.ayojon.repository;

import com.ayojon.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByEventIdOrderByCreatedAtDesc(Long eventId);

    Optional<Review> findByUserIdAndEventId(Long userId, Long eventId);
}

package com.ayojon.repository;

import com.ayojon.entity.EventFollow;
import com.ayojon.entity.UserEventId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventFollowRepository extends JpaRepository<EventFollow, UserEventId> {

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    List<EventFollow> findByUserIdOrderByFollowedAtDesc(Long userId);
}

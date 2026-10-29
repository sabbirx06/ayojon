package com.ayojon.repository;

import com.ayojon.entity.UserBadge;
import com.ayojon.entity.UserBadgeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadgeId> {

    List<UserBadge> findByUserIdOrderByEarnedAtAsc(Long userId);
}

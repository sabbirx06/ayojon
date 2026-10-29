package com.ayojon.repository;

import com.ayojon.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    List<Badge> findAllByOrderByMilestonePointsAsc();
}

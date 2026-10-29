package com.ayojon.repository;

import com.ayojon.entity.Bookmark;
import com.ayojon.entity.UserEventId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, UserEventId> {

    List<Bookmark> findByUserIdOrderByBookmarkedAtDesc(Long userId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    long countByEventId(Long eventId);
}

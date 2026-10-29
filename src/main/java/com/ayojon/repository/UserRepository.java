package com.ayojon.repository;

import com.ayojon.entity.User;
import com.ayojon.repository.projection.LeaderboardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** F3 leaderboard, best first. */
    @Query(value = """
            SELECT leaderboard_rank AS rank, user_id AS userId, name, avatar_url AS avatarUrl, points,
                   events_attended AS eventsAttended, badge_count AS badgeCount
              FROM v_leaderboard
             ORDER BY leaderboard_rank, name
             LIMIT :limit
            """, nativeQuery = true)
    List<LeaderboardEntry> leaderboard(@Param("limit") int limit);
}

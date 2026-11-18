package com.ayojon.model;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgeRepositoryTests {
    private JdbcTemplate jdbc;
    private BadgeRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new BadgeRepository(jdbc);
    }

    @Test
    void findAllBadges_queriesAllBadges() {
        when(jdbc.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(new Badge(1, "First Check-In", 50, "First event")));

        List<Badge> badges = repository.findAllBadges();
        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).name()).isEqualTo("First Check-In");
    }

    @Test
    void findBadgesByUserId_queriesUserBadges() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7)))
                .thenReturn(List.of(new Badge(1, "First Check-In", 50, "First event")));

        List<Badge> badges = repository.findBadgesByUserId(7);
        assertThat(badges).hasSize(1);
    }

    @Test
    void getLeaderboard_queriesLeaderboard() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(10)))
                .thenReturn(List.of(new LeaderboardEntry(7, "Test User", 50, 1)));

        List<LeaderboardEntry> leaderboard = repository.getLeaderboard(10);
        assertThat(leaderboard).hasSize(1);
        assertThat(leaderboard.get(0).name()).isEqualTo("Test User");
    }

    @Test
    void awardAttendancePointsAndBadges_updatesPointsAndInsertsBadges() {
        repository.awardAttendancePointsAndBadges(7, 101);
        verify(jdbc).update("UPDATE users SET points = COALESCE(points, 0) + 50 WHERE user_id = ?", 7);
    }
}

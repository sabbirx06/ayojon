package com.ayojon.repository;

import com.ayojon.entity.Event;
import com.ayojon.entity.enums.EventStatus;
import com.ayojon.repository.projection.EventCard;
import com.ayojon.repository.projection.NearbyEvent;
import com.ayojon.repository.projection.OverlappingEvent;
import com.ayojon.repository.projection.RecommendedEvent;
import com.ayojon.repository.projection.TrendingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    /** Columns of {@code v_event_listing} (alias {@code l}) mapped onto {@link EventCard}. */
    String CARD_COLUMNS = """
            l.event_id AS eventId, l.title, l.status, l.start_time AS startTime, l.end_time AS endTime,
            l.category_name AS categoryName, l.venue_name AS venueName, l.area_name AS areaName,
            l.latitude, l.longitude, l.min_price AS minPrice, l.is_free AS free, l.is_verified AS verified,
            l.poster_url AS posterUrl, l.avg_rating AS avgRating, l.review_count AS reviewCount,
            l.bookmark_count AS bookmarkCount
            """;

    List<Event> findByStatusOrderByStartTimeAsc(EventStatus status);

    /** F18 organizer's own submissions, newest first. */
    List<Event> findByOrganizerIdOrderByCreatedAtDesc(Long organizerId);

    /** Single event card (F6). */
    @Query(value = "SELECT " + CARD_COLUMNS + " FROM v_event_listing l WHERE l.event_id = :eventId",
            nativeQuery = true)
    EventCard findCard(@Param("eventId") Long eventId);

    /**
     * F5 multi-parameter search over upcoming published events. Every argument
     * may be null to skip that filter. Keyword supports web-search syntax.
     */
    @Query(value = "SELECT " + CARD_COLUMNS + """
              FROM search_events(CAST(:keyword AS text), CAST(:from AS timestamptz), CAST(:to AS timestamptz),
                                 CAST(:areaId AS bigint), CAST(:categoryId AS bigint),
                                 CAST(:minPrice AS numeric), CAST(:maxPrice AS numeric)) l
            """, nativeQuery = true)
    List<EventCard> search(@Param("keyword") String keyword,
                           @Param("from") OffsetDateTime from,
                           @Param("to") OffsetDateTime to,
                           @Param("areaId") Long areaId,
                           @Param("categoryId") Long categoryId,
                           @Param("minPrice") BigDecimal minPrice,
                           @Param("maxPrice") BigDecimal maxPrice);

    /** F1 upcoming events within {@code radiusKm} of a GPS point, nearest first. */
    @Query(value = "SELECT " + CARD_COLUMNS + """
                   , n.distance_km AS distanceKm
              FROM events_nearby(CAST(:lat AS numeric), CAST(:lng AS numeric), CAST(:radiusKm AS numeric)) n
              JOIN v_event_listing l ON l.event_id = n.event_id
             ORDER BY n.distance_km
            """, nativeQuery = true)
    List<NearbyEvent> findNearby(@Param("lat") double lat,
                                 @Param("lng") double lng,
                                 @Param("radiusKm") double radiusKm);

    /** F8 trending by bookmarks in the last 7 days. */
    @Query(value = "SELECT " + CARD_COLUMNS + """
                   , l.trend_rank AS trendRank, l.weekly_bookmarks AS weeklyBookmarks
              FROM v_trending_events l
             ORDER BY l.trend_rank, l.start_time
             LIMIT :limit
            """, nativeQuery = true)
    List<TrendingEvent> findTrending(@Param("limit") int limit);

    /** F19 recommendations from the user's bookmarks (popular events if none). */
    @Query(value = "SELECT " + CARD_COLUMNS + """
                   , r.score
              FROM recommend_events(CAST(:userId AS bigint), CAST(:limit AS int)) r
              JOIN v_event_listing l ON l.event_id = r.event_id
             ORDER BY r.score DESC, l.is_verified DESC, l.start_time
            """, nativeQuery = true)
    List<RecommendedEvent> recommendFor(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * F11 live events overlapping a proposed slot. Pass {@code venueId = null}
     * for any venue, and the event's own id as {@code excludeEventId} when editing.
     */
    @Query(value = """
            SELECT event_id AS eventId, title, venue_id AS venueId, start_time AS startTime, end_time AS endTime
              FROM find_overlapping_events(CAST(:start AS timestamptz), CAST(:end AS timestamptz),
                                           CAST(:venueId AS bigint), CAST(:excludeEventId AS bigint))
            """, nativeQuery = true)
    List<OverlappingEvent> findOverlapping(@Param("start") OffsetDateTime start,
                                           @Param("end") OffsetDateTime end,
                                           @Param("venueId") Long venueId,
                                           @Param("excludeEventId") Long excludeEventId);
}

package com.ayojon.repository;

import com.ayojon.entity.ItineraryItem;
import com.ayojon.entity.ItineraryItemId;
import com.ayojon.repository.projection.CalendarEntry;
import com.ayojon.repository.projection.ItinerarySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, ItineraryItemId> {

    /** F10/F11 chronological schedule with a conflict flag on clashing items. */
    @Query(value = """
            SELECT sequence_no AS sequenceNo, event_id AS eventId, title, status, venue_name AS venueName,
                   slot_start AS slotStart, slot_end AS slotEnd, note, has_conflict AS hasConflict
              FROM v_itinerary_schedule WHERE itinerary_id = :itineraryId ORDER BY sequence_no
            """, nativeQuery = true)
    List<ItinerarySlot> schedule(@Param("itineraryId") Long itineraryId);

    /** F13 rows for .ics / CSV export. */
    @Query(value = """
            SELECT uid, summary, description, dtstart, dtend, location, url,
                   ical_status AS icalStatus, last_modified AS lastModified
              FROM v_calendar_export WHERE itinerary_id = :itineraryId ORDER BY dtstart
            """, nativeQuery = true)
    List<CalendarEntry> calendarExport(@Param("itineraryId") Long itineraryId);
}

package com.ayojon.repository;

import com.ayojon.entity.EventChangeLog;
import com.ayojon.repository.projection.ChangeLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventChangeLogRepository extends JpaRepository<EventChangeLog, Long> {

    /** Full history (organizer/admin view), newest first. */
    List<EventChangeLog> findByEventIdOrderByChangedAtDesc(Long eventId);

    /** F17 public audit trail — only changes made after the event went live. */
    @Query(value = """
            SELECT change_id AS changeId, change_type AS changeType, field_name AS fieldName,
                   old_value AS oldValue, new_value AS newValue, note,
                   changed_by_name AS changedByName, changed_at AS changedAt
              FROM v_public_event_change_log WHERE event_id = :eventId
             ORDER BY changed_at DESC, change_id DESC
            """, nativeQuery = true)
    List<ChangeLogEntry> publicLog(@Param("eventId") Long eventId);
}

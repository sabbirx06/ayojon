package com.ayojon.repository;

import com.ayojon.entity.DisputeReport;
import com.ayojon.repository.projection.DisputeQueueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DisputeReportRepository extends JpaRepository<DisputeReport, Long> {

    List<DisputeReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    /** F14 open reports for admins, oldest first. */
    @Query(value = """
            SELECT report_id AS reportId, status, reason_code AS reasonCode, reason_name AS reasonName, details,
                   evidence_url AS evidenceUrl, event_id AS eventId, event_title AS eventTitle,
                   reporter_id AS reporterId, reporter_name AS reporterName, created_at AS createdAt,
                   open_reports_for_event AS openReportsForEvent
              FROM v_dispute_queue ORDER BY created_at
            """, nativeQuery = true)
    List<DisputeQueueItem> openQueue();
}

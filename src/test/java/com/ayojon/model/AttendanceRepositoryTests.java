package com.ayojon.model;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendanceRepositoryTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AttendanceRepository repository = new AttendanceRepository(jdbc);
    private final LocalDateTime checkedInAt = LocalDateTime.of(2026, 11, 12, 10, 30);
    private final String insert = "INSERT INTO attendance(user_id, event_id) VALUES (?, ?) "
            + "RETURNING attendance_id, user_id, event_id, checked_in_at";

    @Test
    void checkInBindsIdentityAndReturnsDatabaseGeneratedFields() throws Exception {
        var row = attendanceRow();
        when(jdbc.query(eq(insert), org.mockito.ArgumentMatchers.<RowMapper<Attendance>>any(), eq(7), eq(12)))
                .thenAnswer(call -> List.of(call.<RowMapper<Attendance>>getArgument(1).mapRow(row, 0)));

        assertEquals(new Attendance(3, 7, 12, checkedInAt), repository.recordCheckIn(7, 12).orElseThrow());
        verify(jdbc).query(eq(insert), org.mockito.ArgumentMatchers.<RowMapper<Attendance>>any(), eq(7), eq(12));
    }

    @Test
    void duplicateCheckInReturnsEmptyWithoutAnExistenceRace() {
        when(jdbc.query(eq(insert), org.mockito.ArgumentMatchers.<RowMapper<Attendance>>any(), eq(7), eq(12)))
                .thenThrow(new DuplicateKeyException("duplicate user and event"));

        assertTrue(repository.recordCheckIn(7, 12).isEmpty());
        verifyNoMoreInteractionsAfterInsert();
    }

    @Test
    void foreignKeyFailureIsNotReportedAsDuplicate() {
        when(jdbc.query(eq(insert), org.mockito.ArgumentMatchers.<RowMapper<Attendance>>any(), eq(7), eq(12)))
                .thenThrow(new DataIntegrityViolationException("missing event"));

        assertThrows(DataIntegrityViolationException.class, () -> repository.recordCheckIn(7, 12));
    }

    @Test
    void historyBindsUserAndMapsRowsInDatabaseOrder() throws Exception {
        var row = attendanceRow();
        String sql = "SELECT attendance_id, user_id, event_id, checked_in_at FROM attendance "
                + "WHERE user_id = ? ORDER BY checked_in_at DESC";
        when(jdbc.query(eq(sql), org.mockito.ArgumentMatchers.<RowMapper<Attendance>>any(), eq(7)))
                .thenAnswer(call -> List.of(call.<RowMapper<Attendance>>getArgument(1).mapRow(row, 0)));

        assertEquals(List.of(new Attendance(3, 7, 12, checkedInAt)), repository.findByUserId(7));
        assertTrue(repository.findByUserId(99).isEmpty());
    }

    @Test
    void nullableTimestampFromExistingSchemaIsPreserved() throws Exception {
        var row = attendanceRow();
        when(row.getObject("checked_in_at", LocalDateTime.class)).thenReturn(null);
        when(jdbc.query(eq(insert), org.mockito.ArgumentMatchers.<RowMapper<Attendance>>any(), eq(7), eq(12)))
                .thenAnswer(call -> List.of(call.<RowMapper<Attendance>>getArgument(1).mapRow(row, 0)));

        assertNull(repository.recordCheckIn(7, 12).orElseThrow().checkedInAt());
    }

    @Test
    void statusBindsBothIdsAndReturnsTrueOrFalse() {
        String sql = "SELECT EXISTS (SELECT 1 FROM attendance WHERE user_id = ? AND event_id = ?)";
        when(jdbc.queryForObject(sql, Boolean.class, 7, 12)).thenReturn(true);
        when(jdbc.queryForObject(sql, Boolean.class, 7, 13)).thenReturn(false);

        assertTrue(repository.isUserCheckedIn(7, 12));
        assertFalse(repository.isUserCheckedIn(7, 13));
    }

    @Test
    void countIsScopedToEventIncludingZeroAttendance() {
        String sql = "SELECT COUNT(*) FROM attendance WHERE event_id = ?";
        when(jdbc.queryForObject(sql, Integer.class, 12)).thenReturn(4);
        when(jdbc.queryForObject(sql, Integer.class, 13)).thenReturn(0);

        assertEquals(4, repository.countByEventId(12));
        assertEquals(0, repository.countByEventId(13));
    }

    private ResultSet attendanceRow() throws Exception {
        var row = mock(ResultSet.class);
        when(row.getInt("attendance_id")).thenReturn(3);
        when(row.getInt("user_id")).thenReturn(7);
        when(row.getInt("event_id")).thenReturn(12);
        when(row.getObject("checked_in_at", LocalDateTime.class)).thenReturn(checkedInAt);
        return row;
    }

    private void verifyNoMoreInteractionsAfterInsert() {
        verify(jdbc).query(eq(insert), org.mockito.ArgumentMatchers.<RowMapper<Attendance>>any(), eq(7), eq(12));
        verifyNoMoreInteractions(jdbc);
    }
}

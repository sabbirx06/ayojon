package com.ayojon.model;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserRepositoryTests {
    @Test
    void lookupBindsEmailAndMapsAccountColumns() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var row = mock(ResultSet.class);
        when(row.getInt("user_id")).thenReturn(7);
        when(row.getString("name")).thenReturn("Test User");
        when(row.getString("email")).thenReturn("user@example.com");
        when(row.getString("role")).thenReturn("USER");
        when(row.getObject("points", Integer.class)).thenReturn(12);
        when(row.getString("password_hash")).thenReturn("stored-hash");
        when(jdbc.query(eq("SELECT user_id, name, email, role, points, password_hash FROM users WHERE email = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<User>>any(), eq("user@example.com")))
                .thenAnswer(call -> List.of(call.<RowMapper<User>>getArgument(1).mapRow(row, 0)));

        var repository = new UserRepository(jdbc);
        assertEquals(new User(7, "Test User", "user@example.com", "USER", 12, "stored-hash"),
                repository.findByEmail("user@example.com").orElseThrow());
        assertTrue(repository.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    void createBindsValuesWithoutChangingStoredHash() {
        var jdbc = mock(JdbcTemplate.class);
        new UserRepository(jdbc).create("O'Brien", "user@example.com", "stored-hash", "USER");
        verify(jdbc).update("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?)",
                "O'Brien", "user@example.com", "stored-hash", "USER");
    }
}

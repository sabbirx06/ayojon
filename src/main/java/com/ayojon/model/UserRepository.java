package com.ayojon.model;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.query("SELECT user_id, name, email, role, points, password_hash FROM users WHERE email = ?",
                (row, index) -> new User(row.getInt("user_id"), row.getString("name"), row.getString("email"),
                        row.getString("role"), row.getObject("points", Integer.class), row.getString("password_hash")),
                email).stream().findFirst();
    }

    public void create(String name, String email, String passwordHash, String role) {
        jdbc.update("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?)",
                name, email, passwordHash, role);
    }

    public java.util.List<User> findAll() {
        return jdbc.query("SELECT user_id, name, email, role, points, password_hash FROM users ORDER BY created_at DESC",
                (row, index) -> new User(row.getInt("user_id"), row.getString("name"), row.getString("email"),
                        row.getString("role"), row.getObject("points", Integer.class), row.getString("password_hash")));
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        return count != null ? count : 0;
    }
}

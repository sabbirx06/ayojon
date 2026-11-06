package com.ayojon;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Principal principal) {
        if (principal == null) return Map.of("authenticated", false);
        var users = jdbc.queryForList("SELECT user_id AS id, name, email, role, points FROM users WHERE email=?", principal.getName());
        return users.isEmpty() ? Map.of("authenticated", false) : Map.of("authenticated", true, "user", users.getFirst());
    }

    public record Signup(String name, String email, String password, String role) {}

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> invalidRequest(ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason() == null ? "Request could not be completed." : exception.getReason()));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> signup(@RequestBody Signup request) {
        String name = request.name() == null ? "" : request.name().trim();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String password = request.password() == null ? "" : request.password();
        if (name.isEmpty() || name.length() > 100 || email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a name, a valid email, and a password of 8–72 bytes.");
        }
        String role = "organizer".equals(request.role()) ? "ORGANIZER" : "USER";
        try {
            jdbc.update("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?)", name, email, passwords.encode(password), role);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        return Map.of("message", "Your account is ready. Log in to continue.");
    }
}

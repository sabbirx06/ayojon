package com.ayojon;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT email, password_hash, role FROM users WHERE email = ?",
                (row, index) -> User.withUsername(row.getString("email"))
                        .password(row.getString("password_hash")).roles(row.getString("role")).build(),
                email.trim().toLowerCase(java.util.Locale.ROOT))
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/favicon.ico", "/assets/branding/**", "/assets/js/**", "/error",
                                "/api/auth/csrf", "/api/auth/signup", "/api/auth/me").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                        .successHandler((request, response, authentication) -> {
                            response.setContentType("application/json");
                            response.getWriter().write("{\"ok\":true}");
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Email or password is incorrect.\"}");
                        }).permitAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> response.setStatus(401)))
                .build();
    }
}

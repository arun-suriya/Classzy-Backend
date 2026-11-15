package com.centram.classzy.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMinutes;

    public JwtService(@Value("${classzy.jwt.secret}") String secret,
                      @Value("${classzy.jwt.expiry-minutes}") long expiryMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("classzy.jwt.secret must be at least 32 characters long.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiryMinutes = expiryMinutes;
    }

    public long expirySeconds() {
        return expiryMinutes * 60;
    }

    public String issue(AppPrincipal p) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(p.userId()))
                .claim("email", p.email())
                .claim("role", p.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds())));
        if (p.institutionId() != null) {
            builder.claim("inst", String.valueOf(p.institutionId()));
        }
        return builder.signWith(key).compact();
    }

    public Optional<AppPrincipal> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String inst = c.get("inst", String.class);
            return Optional.of(new AppPrincipal(
                    Long.valueOf(c.getSubject()),
                    c.get("email", String.class),
                    Role.valueOf(c.get("role", String.class)),
                    inst == null ? null : Long.valueOf(inst)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

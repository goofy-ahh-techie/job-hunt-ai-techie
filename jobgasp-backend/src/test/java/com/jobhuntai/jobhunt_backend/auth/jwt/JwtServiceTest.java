package com.jobhuntai.jobhunt_backend.auth.jwt;

import com.jobhuntai.jobhunt_backend.user.Role;
import com.jobhuntai.jobhunt_backend.user.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256!!";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, "job-hunt-copilot", 3600));

    private static User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("alex@example.com")
                .passwordHash("irrelevant")
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    @Test
    void generatedTokenRoundTripsToItsSubject() {
        User user = user();

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractSubject(token)).contains("alex@example.com");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreignToken = new JwtService(new JwtProperties(
                "a-completely-different-secret-key-value-here", "job-hunt-copilot", 3600))
                .generateAccessToken(user());

        assertThat(jwtService.extractSubject(foreignToken)).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreignIssuerToken = new JwtService(new JwtProperties(SECRET, "someone-else", 3600))
                .generateAccessToken(user());

        assertThat(jwtService.extractSubject(foreignIssuerToken)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = new JwtService(new JwtProperties(SECRET, "job-hunt-copilot", -60))
                .generateAccessToken(user());

        assertThat(jwtService.extractSubject(expired)).isEmpty();
    }

    @Test
    void garbageTokenIsRejectedWithoutThrowing() {
        assertThat(jwtService.extractSubject("not.a.jwt")).isEqualTo(Optional.empty());
    }
}

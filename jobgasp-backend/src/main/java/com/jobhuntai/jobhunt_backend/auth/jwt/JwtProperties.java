package com.jobhuntai.jobhunt_backend.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from {@code app.security.jwt.*}. Never hardcode the secret —
 * it comes from the environment per profile.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, String issuer, long accessTokenTtlSeconds) {
}
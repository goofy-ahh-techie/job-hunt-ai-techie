package com.jobhuntai.jobhunt_backend.auth.service;

import com.jobhuntai.jobhunt_backend.auth.dto.AuthResponse;
import com.jobhuntai.jobhunt_backend.auth.dto.LoginRequest;
import com.jobhuntai.jobhunt_backend.auth.dto.RegisterRequest;
import com.jobhuntai.jobhunt_backend.auth.exception.EmailAlreadyRegisteredException;
import com.jobhuntai.jobhunt_backend.auth.jwt.JwtService;
import com.jobhuntai.jobhunt_backend.user.Role;
import com.jobhuntai.jobhunt_backend.user.User;
import com.jobhuntai.jobhunt_backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.USER)
                .enabled(true)
                .build();

        return toAuthResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                // Same message for unknown email and wrong password — do not leak
                // which accounts exist.
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.bearer(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                jwtService.generateAccessToken(user),
                jwtService.accessTokenTtlSeconds());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}

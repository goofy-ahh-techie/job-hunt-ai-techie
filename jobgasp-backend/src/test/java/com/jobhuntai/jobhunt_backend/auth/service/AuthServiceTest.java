package com.jobhuntai.jobhunt_backend.auth.service;

import com.jobhuntai.jobhunt_backend.auth.dto.AuthResponse;
import com.jobhuntai.jobhunt_backend.auth.dto.LoginRequest;
import com.jobhuntai.jobhunt_backend.auth.dto.RegisterRequest;
import com.jobhuntai.jobhunt_backend.auth.exception.EmailAlreadyRegisteredException;
import com.jobhuntai.jobhunt_backend.auth.jwt.JwtProperties;
import com.jobhuntai.jobhunt_backend.auth.jwt.JwtService;
import com.jobhuntai.jobhunt_backend.user.Role;
import com.jobhuntai.jobhunt_backend.user.User;
import com.jobhuntai.jobhunt_backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final Map<String, User> store = new HashMap<>();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService(new JwtProperties("test-secret-that-is-long-enough-for-hs256!!", "job-hunt-copilot", 3600));

    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        when(userRepository.existsByEmailIgnoreCase(anyString()))
                .thenAnswer(inv -> store.containsKey(inv.getArgument(0, String.class).toLowerCase()));
        when(userRepository.findByEmailIgnoreCase(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0, String.class).toLowerCase())));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0, User.class);
            store.put(saved.getEmail(), saved);
            return saved;
        });

        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerStoresHashedPasswordAndReturnsUsableToken() {
        AuthResponse response = authService.register(
                new RegisterRequest("Alex@Example.com", "correct-horse", "Alex"));

        User stored = store.get("alex@example.com");
        assertThat(stored).isNotNull();
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct-horse");
        assertThat(passwordEncoder.matches("correct-horse", stored.getPasswordHash())).isTrue();
        assertThat(stored.getRole()).isEqualTo(Role.USER);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(jwtService.extractSubject(response.accessToken())).contains("alex@example.com");
    }

    @Test
    void registerRejectsDuplicateEmailRegardlessOfCasing() {
        authService.register(new RegisterRequest("alex@example.com", "correct-horse", "Alex"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("ALEX@example.com", "another-password", "Alex Again")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        authService.register(new RegisterRequest("alex@example.com", "correct-horse", "Alex"));

        AuthResponse response = authService.login(new LoginRequest("alex@example.com", "correct-horse"));

        assertThat(jwtService.extractSubject(response.accessToken())).contains("alex@example.com");
    }

    @Test
    void loginRejectsWrongPassword() {
        authService.register(new RegisterRequest("alex@example.com", "correct-horse", "Alex"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alex@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginOnUnknownEmailGivesSameMessageAsWrongPassword() {
        authService.register(new RegisterRequest("alex@example.com", "correct-horse", "Alex"));

        String unknownEmailMessage = messageOf(() ->
                authService.login(new LoginRequest("nobody@example.com", "correct-horse")));
        String wrongPasswordMessage = messageOf(() ->
                authService.login(new LoginRequest("alex@example.com", "wrong-password")));

        assertThat(unknownEmailMessage).isEqualTo(wrongPasswordMessage);
    }

    @Test
    void disabledAccountCannotLogIn() {
        authService.register(new RegisterRequest("alex@example.com", "correct-horse", "Alex"));
        store.get("alex@example.com").setEnabled(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alex@example.com", "correct-horse")))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static String messageOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected the login to fail");
        } catch (BadCredentialsException ex) {
            return ex.getMessage();
        }
    }
}

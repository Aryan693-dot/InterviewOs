package com.interviewos.service;

import com.interviewos.dto.AuthRequest;
import com.interviewos.dto.AuthResponse;
import com.interviewos.dto.RegisterRequest;
import com.interviewos.model.User;
import com.interviewos.repository.UserRepository;
import com.interviewos.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Replaces controllers/authController.js.
 *
 * BCryptPasswordEncoder (from SecurityConfig) replaces bcrypt npm.
 * JwtUtil replaces jsonwebtoken npm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Value("${app.email-verification.required:false}")
    private boolean emailVerificationRequired;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(passwordEncoder.encode(req.getPassword())); // BCrypt

        if (emailVerificationRequired) {
            user.setEmailVerified(false);
            user.setVerificationToken(UUID.randomUUID().toString());
            user.setVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));
            emailService.sendVerificationEmail(user.getEmail(), user.getName(),
                frontendUrl + "/verify-email?token=" + user.getVerificationToken());
        } else {
            user.setEmailVerified(true);
        }

        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, sanitize(user));
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
            .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (emailVerificationRequired && !user.isEmailVerified()) {
            throw new RuntimeException("Please verify your email first");
        }

        // Update streak
        updateStreak(user);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, sanitize(user));
    }

    public Object getProfile(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return sanitize(user);
    }

    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setResetToken(UUID.randomUUID().toString());
            user.setResetTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(email,
                frontendUrl + "/reset-password?token=" + user.getResetToken());
        });
        // always succeed (don't reveal if email exists)
    }

    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        if (user.getResetTokenExpiry().isBefore(Instant.now())) {
            throw new RuntimeException("Reset token expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new RuntimeException("Verification token expired");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void updateStreak(User user) {
        Instant now = Instant.now();
        if (user.getLastActiveAt() == null) {
            user.setStreakDays(1);
            return;
        }
        long daysSinceLast = ChronoUnit.DAYS.between(user.getLastActiveAt(), now);
        if (daysSinceLast == 1)       user.setStreakDays(user.getStreakDays() + 1);
        else if (daysSinceLast > 1)   user.setStreakDays(1);
        // Same day → no change
    }

    /** Strip sensitive fields before sending to frontend */
    private java.util.Map<String, Object> sanitize(User user) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id",               user.getId());
        map.put("name",             user.getName());
        map.put("email",            user.getEmail());
        map.put("currentLevel",     user.getCurrentLevel());
        map.put("eloRating",        user.getEloRating());
        map.put("streakDays",       user.getStreakDays());
        map.put("badges",           user.getBadges());
        map.put("topicProficiency", user.getTopicProficiency());
        map.put("emailVerified",    user.isEmailVerified());
        map.put("createdAt",        user.getCreatedAt());
        return map;
    }
}

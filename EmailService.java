package com.interviewos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.interviewos.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Replaces nodemailer in the original Node.js backend.
 *
 * Spring Mail (JavaMailSender) is the Java equivalent.
 * @Async means emails are sent on a virtual thread, non-blocking.
 * @Scheduled replaces the node-cron daily reminder job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Async
    public void sendVerificationEmail(String to, String name, String verifyUrl) {
        String html = """
            <h2>Welcome to InterviewOS, %s!</h2>
            <p>Click the link below to verify your email address:</p>
            <a href="%s" style="background:#6366f1;color:white;padding:12px 24px;
               border-radius:8px;text-decoration:none;">Verify Email</a>
            <p>This link expires in 24 hours.</p>
            """.formatted(name, verifyUrl);
        sendHtml(to, "Verify your InterviewOS email", html);
    }

    @Async
    public void sendPasswordResetEmail(String to, String resetUrl) {
        String html = """
            <h2>Reset your InterviewOS password</h2>
            <p>Click the link below to reset your password:</p>
            <a href="%s" style="background:#ef4444;color:white;padding:12px 24px;
               border-radius:8px;text-decoration:none;">Reset Password</a>
            <p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>
            """.formatted(resetUrl);
        sendHtml(to, "Reset your InterviewOS password", html);
    }

    /**
     * Daily practice reminder — replaces node-cron job.
     * Runs every day at 9:00 AM UTC.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyReminders() {
        log.info("Sending daily practice reminders...");
        userRepository.findAll().forEach(user -> {
            if (user.getEmail() != null && user.isEmailVerified()) {
                String html = """
                    <h2>🎯 Time to practice, %s!</h2>
                    <p>Your current level: <strong>%s</strong> | Elo: <strong>%d</strong></p>
                    <p>Keep your %d-day streak going!</p>
                    <a href="https://interviewos.app/interview"
                       style="background:#6366f1;color:white;padding:12px 24px;
                              border-radius:8px;text-decoration:none;">
                       Start Practice →
                    </a>
                    """.formatted(user.getName(), user.getCurrentLevel(),
                                  user.getEloRating(), user.getStreakDays());
                sendHtml(user.getEmail(), "🎯 Daily InterviewOS Practice Reminder", html);
            }
        });
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.debug("Email sent to {}: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}

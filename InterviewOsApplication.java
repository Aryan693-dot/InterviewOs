package com.interviewos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * InterviewOS — AI-Free Adaptive DSA & Interview Practice Platform
 *
 * Java / Spring Boot equivalent of the original MERN stack project.
 *
 * Stack:
 *   Backend  : Spring Boot 3 + Spring Security + Spring Data MongoDB
 *   JWT      : jjwt 0.12
 *   WebSocket: Spring STOMP (replaces Socket.io)
 *   Mail     : Spring Mail (replaces nodemailer)
 *   Frontend : React (unchanged from original)
 */
@SpringBootApplication
@EnableScheduling   // for daily practice reminder emails
public class InterviewOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewOsApplication.class, args);
    }
}

# InterviewOS — Full-Stack Java Edition

A complete migration of the MERN-stack InterviewOS into a **Java / Spring Boot** backend while keeping the React frontend intact.

---

## Stack Comparison

| Original (MERN) | Java Edition |
|---|---|
| Node.js + Express | **Spring Boot 3.3** |
| Mongoose ODM | **Spring Data MongoDB** |
| `bcrypt` npm | **BCryptPasswordEncoder** (Spring Security) |
| `jsonwebtoken` npm | **jjwt 0.12** |
| Socket.io | **Spring WebSocket + STOMP** |
| `nodemailer` | **Spring Mail (JavaMailSender)** |
| `express-rate-limit` | **Bucket4j** |
| `morgan` | **Spring Boot Actuator + Logback** |
| `helmet` | **Spring Security headers** |
| `node-cron` | **@Scheduled** |
| `child_process` | **ProcessBuilder** |
| MongoDB Atlas | **MongoDB Atlas** (unchanged) |
| React frontend | **React** (unchanged) |

---

## Prerequisites

- **Java 21** (uses virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`)
- **Maven 3.9+** (or use the bundled `./mvnw`)
- **MongoDB** running locally or MongoDB Atlas URI
- **Node.js 18+** — only needed by the code judge sandbox (to run JS submissions)
- **Python 3** — optional, for Python submission support

---

## Quick Start

### 1. Backend

```bash
cd backend

# Copy env template
cp src/main/resources/application.properties src/main/resources/application-local.properties

# Edit application-local.properties:
#   spring.data.mongodb.uri=mongodb://localhost:27017/interviewos
#   app.jwt.secret=your-256-bit-secret-here

# Run (seeds 5 starter questions automatically on first boot)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Backend runs on `http://localhost:5000`.

### 2. Frontend

The React frontend is **unchanged** from the original — it talks to the same API shape.

```bash
cd frontend
npm install
npm start
```

Frontend runs on `http://localhost:3000`.

---

## Project Structure

```
interviewos-java/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/interviewos/
│       ├── InterviewOsApplication.java     ← Spring Boot entry (replaces server.js)
│       ├── config/
│       │   ├── SecurityConfig.java          ← helmet + cors → Spring Security
│       │   ├── WebSocketConfig.java         ← Socket.io → STOMP WebSocket
│       │   └── DataSeeder.java              ← seed.js → CommandLineRunner
│       ├── controller/
│       │   ├── AuthController.java          ← /api/auth/**
│       │   ├── QuestionController.java      ← /api/questions/**
│       │   ├── JudgeController.java         ← /api/judge/**
│       │   └── AnalyticsController.java     ← /api/analytics/**
│       ├── dto/                             ← Request/Response POJOs
│       ├── exception/
│       │   └── GlobalExceptionHandler.java  ← Express error middleware
│       ├── model/
│       │   ├── User.java                    ← Mongoose User schema
│       │   ├── Question.java                ← Mongoose Question schema
│       │   └── Submission.java              ← Mongoose Submission schema
│       ├── repository/                      ← MongoRepository (replaces Mongoose queries)
│       ├── security/
│       │   ├── JwtUtil.java                 ← jsonwebtoken npm → jjwt
│       │   └── JwtAuthFilter.java           ← authMiddleware.js → OncePerRequestFilter
│       ├── service/
│       │   ├── AuthService.java             ← authController.js
│       │   ├── JudgeService.java            ← judge + adaptiveEngine logic
│       │   ├── QuestionService.java
│       │   ├── AnalyticsService.java        ← MongoDB aggregation pipelines
│       │   └── EmailService.java            ← nodemailer → JavaMailSender
│       ├── util/
│       │   ├── CodeRunner.java              ← utils/codeRunner.js → ProcessBuilder
│       │   └── AdaptiveEngine.java          ← utils/adaptiveEngine.js (rule-based)
│       └── websocket/
│           └── CollabRoomController.java    ← Socket.io room events → STOMP
└── frontend/                               ← React (unchanged)
```

---

## Frontend WebSocket Migration

The frontend needs **one change** — swap `socket.io-client` for `@stomp/stompjs`:

```bash
cd frontend
npm remove socket.io-client
npm install @stomp/stompjs sockjs-client
```

Then in `CollabRoom.js`:

```js
// BEFORE (Socket.io)
import { io } from 'socket.io-client';
const socket = io(SOCKET_URL);
socket.emit('join-room', roomId);
socket.on('code-change', setCode);

// AFTER (STOMP)
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
  webSocketFactory: () => new SockJS(`${API_URL}/ws`),
  onConnect: () => {
    client.subscribe(`/topic/room/${roomId}`, (msg) => {
      const data = JSON.parse(msg.body);
      setCode(data.code);
    });
  }
});
client.activate();

// Send code change
client.publish({
  destination: `/app/room/${roomId}/code`,
  body: JSON.stringify({ code: newCode })
});
```

---

## API Routes (identical to original)

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register |
| POST | `/api/auth/login` | Login → JWT |
| GET | `/api/auth/me` | Profile (protected) |
| POST | `/api/auth/forgot-password` | Send reset email |
| POST | `/api/auth/reset-password` | Reset with token |
| GET | `/api/auth/verify-email?token=` | Verify email |
| GET | `/api/questions` | List questions |
| GET | `/api/questions/:id` | Single question |
| POST | `/api/questions` | Create question (admin) |
| POST | `/api/judge/submit` | Submit code (saves submission) |
| POST | `/api/judge/run` | Run code (no save) |
| GET | `/api/judge/review/:id` | Review submission |
| GET | `/api/analytics/me` | Personal analytics |
| GET | `/api/analytics/leaderboard` | Global leaderboard |
| GET | `/actuator/health` | Health check (replaces `/api/health`) |

---

## Supported Languages in Code Judge

| Language | Executor | Notes |
|---|---|---|
| JavaScript | `node solution.js` | Requires Node.js on server |
| Python | `python3 solution.py` | Optional |
| **Java** | `javac Solution.java && java Solution` | **New — not in original** |

---

## Production Deployment

### Environment Variables

```properties
MONGO_URI=mongodb+srv://user:pass@cluster.mongodb.net/interviewos
JWT_SECRET=your-very-long-256-bit-random-secret
MAIL_HOST=smtp.gmail.com
MAIL_USER=your@gmail.com
MAIL_PASS=your-app-password
CORS_ORIGIN=https://your-frontend.vercel.app
PORT=5000
```

### Build JAR

```bash
cd backend
./mvnw clean package -DskipTests -Dspring.profiles.active=prod
java -jar target/interviewos-backend-1.0.0.jar
```

### Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/interviewos-backend-1.0.0.jar app.jar
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

## Key Java-vs-Node Decisions

### Why Spring Security over manual middleware?
Spring Security gives you BCrypt, CORS, CSRF, security headers, and JWT filter chains in one composable config — equivalent to `helmet + bcrypt + express-jwt + cors` combined.

### Why STOMP over a Socket.io Java port?
STOMP is a standard messaging protocol supported natively by Spring, browsers (via SockJS), and every load balancer. Socket.io is Node-specific and requires adapter config for multi-instance deployments.

### Why jjwt over nimbus-jose-jwt?
jjwt has a simpler builder API that mirrors the original `jwt.sign()` / `jwt.verify()` usage pattern.

### Why ProcessBuilder over a JVM-native sandbox?
The original codeRunner.js used `child_process` — ProcessBuilder is the exact Java equivalent. For production, swap to Docker sandbox (same `USE_DOCKER_SANDBOX` flag works).

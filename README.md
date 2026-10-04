# seat-reservation-service
Seat reservation service - Build, deploy, and operate a small service that sells assigned seats for an event (a concert or a movie hall) and lets users reserve them. The whole challenge is correctness under load: you must never sell the same seat twice, never let a user exceed their booking limit, and never double-charge a retried request 
## Important Race Scenarios This Design Handles

| Scenario | Result |
|---|---|
| User cancels confirmed reservation | Seat becomes `AVAILABLE` |
| Same cancellation called twice | Second call returns existing `CANCELLED` state |
| 20 cancellation requests simultaneously | One performs transition; others observe `CANCELLED` |
| Another user tries to cancel | Returns `403` |
| Unknown reservation ID | Returns `404` |
| User cancels multi-seat reservation | All seats are released atomically |
| Counter would become negative | Transaction fails and rolls back |
| Seat doesn't belong to reservation | Transaction fails and rolls back |
| Redis unavailable | PostgreSQL locks still guarantee correctness |
| Reserve and cancel overlap | Same seat/user Redis resources serialize them |
| Cancelled seat gets rebooked | New reservation owns the seat |
| Old cancellation retry after rebooking | Old reservation is already `CANCELLED`; new reservation remains untouched |
| Client loses original reserve response | Replay idempotency key → original reservation → then cancel |

# Seat Reservation at Scale

A production-oriented seat reservation service built with Java 21, Spring Boot, PostgreSQL and Redis/Redisson.

The system is designed to prevent double-selling under heavy concurrent load while supporting idempotent reservation requests, per-user booking limits and explicit cancellation.

## Technology Stack

- Java 21
- Spring Boot 4
- Spring MVC
- Spring Data JPA
- PostgreSQL
- Flyway
- Redis
- Redisson
- Spring Security
- JWT
- Micrometer
- Prometheus
- Docker
- Nginx
- k6

## Architecture

PostgreSQL is the final source of truth.

Redis is used only as a contention optimization.

```text
Client
  |
  v
Nginx
  |
  +----------------+
  |                |
  v                v
App Instance 1   App Instance 2
  |                |
  +-------+--------+
          |
     +----+----+
     |         |
     v         v
 PostgreSQL   Redis
```

### Correctness rule

> Redis = contention optimization. PostgreSQL = correctness.

Even if Redis becomes unavailable, PostgreSQL row locking and transactional validation continue protecting reservations.

## Main Features

- JWT authentication
- User identity obtained from JWT rather than request body
- Admin show creation
- Seat inventory
- Atomic multi-seat reservation
- No double-selling
- Per-user reservation limit
- PostgreSQL-backed idempotency
- Same idempotency key + same request returns original reservation
- Same idempotency key + different request returns conflict
- Explicit cancellation
- Idempotent cancellation
- Redis distributed locking
- PostgreSQL fallback when Redis is unavailable
- Fast rejection for already-confirmed seats
- Prometheus metrics
- Liveness/readiness probes
- Request correlation using X-Request-ID
- Structured JSON logs
- Dockerized multi-instance deployment
- k6 concurrency/load testing

## API

### Register

```http
POST /auth/register
```

### Login

```http
POST /auth/login
```

### Create show

```http
POST /shows
```

Example:

```json
{
  "name": "friday-night",
  "seats": [
    "A1",
    "A2",
    "A3"
  ],
  "price_paise": 25000
}
```

### Get show

```http
GET /shows/{showId}
```

### Reserve seats

```http
POST /shows/{showId}/reserve
```

Example:

```json
{
  "seats": [
    "A1",
    "A2"
  ],
  "idempotency_key": "payment-request-123"
}
```

Authenticated user identity is derived from JWT.

### Cancel reservation

```http
POST /reservations/{reservationId}/cancel
```

Cancellation is idempotent.

## Seat states

This implementation uses explicit cancellation rather than expiring holds.

Therefore:

```text
held = 0
```

The inventory invariant is:

```text
available + held + confirmed = total
```

## Concurrency Strategy

Reservation requests perform:

1. Normalize requested seats.
2. Validate duplicate seats.
3. Read-only fast rejection for already-sold seats.
4. Bounded Redis distributed lock attempt.
5. PostgreSQL transaction.
6. Lock idempotency state.
7. Lock user/show booking counter.
8. Lock requested seats using pessimistic row locks.
9. Revalidate availability.
10. Create reservation.
11. Assign all seats atomically.
12. Update per-user count.
13. Complete idempotency record.
14. Commit.

The database transaction is the final correctness boundary.

## Idempotency

Idempotency records are stored in PostgreSQL.

Key:

```text
user_id + idempotency_key
```

The request hash includes:

```text
showId + normalized sorted seat list
```

Behavior:

```text
same key + same request
=> original response

same key + different request
=> HTTP 409
```

## Cancellation

Cancellation:

- verifies authenticated ownership
- locks reservation
- locks per-user counter
- locks original seats
- releases seats
- decrements booking count exactly once
- marks reservation CANCELLED
- preserves reservation-seat history

Repeated cancellation returns the already-cancelled reservation.

## Redis Failure

Redis is optional for correctness.

If Redis fails:

```text
Redis operation fails
      |
      v
PostgreSQL transaction
      |
      v
pessimistic locking
      |
      v
correct reservation result
```

## Health

Liveness:

```text
GET /actuator/health/liveness
```

Readiness:

```text
GET /actuator/health/readiness
```

Prometheus:

```text
GET /actuator/prometheus
```

## Request IDs

Every response contains:

```text
X-Request-ID
```

If the caller sends a valid `X-Request-ID`, it is propagated.

Otherwise the service generates a UUID.

The ID is stored in SLF4J MDC and is available in structured application logs.

## Prometheus Business Metrics

Custom metrics include:

```text
seat_reservation_requests_total
seat_reservation_responses_total
seat_reservation_duration_seconds
seat_cancellation_requests_total
seat_cancellation_responses_total
seat_cancellation_duration_seconds
```

Spring Boot also exports JVM, HTTP, datasource and process metrics.

## Build

```powershell
mvn clean package -DskipTests
```

## Local Services

Start PostgreSQL and Redis.

Then run two application instances:

```powershell
java -jar .\target\seat-reservation-service-0.0.1-SNAPSHOT.jar --server.port=8080
```

and:

```powershell
java -jar .\target\seat-reservation-service-0.0.1-SNAPSHOT.jar --server.port=8081
```

## Docker

Set:

```powershell
$env:JWT_SECRET="replace-with-a-long-random-secret-at-least-32-bytes"
```

Then:

```powershell
docker compose -f .\docker\docker-compose.full.yml up --build -d
```

Check:

```powershell
docker compose -f .\docker\docker-compose.full.yml ps
```

Health:

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/health/readiness"
```

## Load Test Results

### Optimized 5,000 hot-seat test

```text
1 successful reservation
4,999 clean conflicts
0 HTTP 5xx
0 network errors
~113.7 reservation attempts/sec
p95 ~1.5 sec
```

### Optimized 5,000 cancellation test

```text
5,000 HTTP 200 responses
0 HTTP 5xx
0 network errors
~67.1 cancellation requests/sec
p95 ~2.34 sec
```

A previous 200,000-request experiment intentionally is not reported as a successful benchmark because one local application process was shut down during that run.

## Required Final Tests

Included k6 tests:

```text
load-test/hot-seat-500.js
load-test/per-user-limit.js
load-test/burst-20k.js
load-test/cancel-5000.js
```

The final expected correctness proof is:

```text
500 simultaneous same-seat:
1 × 201
499 × 409
0 × 5xx

Per-user limit:
4 × 201
4 × 409

20k burst:
1 × 201
19,999 × 409

Cancellation:
5,000 × 200
```

## Database Double-Selling Verification

```sql
SELECT
    rs.show_id,
    rs.seat_no,
    COUNT(*) AS confirmed_reservations
FROM reservation_seats rs
JOIN reservations r
    ON r.id = rs.reservation_id
WHERE r.status = 'CONFIRMED'
GROUP BY
    rs.show_id,
    rs.seat_no
HAVING COUNT(*) > 1;
```

Expected:

```text
0 rows
```

## Deployment

The Docker stack can be deployed to any Linux VM or container host.

Only the HTTP gateway should be publicly exposed.

PostgreSQL and Redis should remain private.

See:

```text
docs/DEPLOYMENT.md
```

for deployment instructions.
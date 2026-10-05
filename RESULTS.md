# Submission Evidence & Test Results

## Submission Links

- **Live URL:** https://seat-reservation-service-2kyt.onrender.com
- **GitHub:** https://github.com/yashupadhyay266/seat-reservation-service
- **Readiness:** https://seat-reservation-service-2kyt.onrender.com/actuator/health/readiness
- **Liveness:** https://seat-reservation-service-2kyt.onrender.com/actuator/health/liveness
- **Prometheus:** https://seat-reservation-service-2kyt.onrender.com/actuator/prometheus

## Live Deployment Evidence

The service is deployed on Render with managed PostgreSQL and Redis/Key Value.

Verified from the live deploy:

- Spring Boot started successfully on Render.
- PostgreSQL connection established.
- Flyway validated and applied migrations V1, V2 and V3 to a fresh database.
- Redis/Redisson connected successfully.
- Render marked the service live.
- Live readiness endpoint returned **UP**.
- Live liveness endpoint returned **UP**.
- Prometheus endpoint returned HTTP **200**.
- A cold deployment startup completed successfully in about **175 seconds**.

## Functional Requirement Coverage

| Requirement | Status | Evidence |
|---|---|---|
| Admin creates show with integer paise | ✅ | `POST /shows`; money stored as integer paise |
| Authenticated reservation, token-derived identity | ✅ | Reservation controller derives user identity from JWT subject |
| No double-selling | ✅ | Exact 500-way hot-seat test: 1 winner, 499 conflicts, 0 5xx |
| Per-user limit, default 4 | ✅ | 8 concurrent requests by one user: 4 × 201, 4 × 409 |
| Idempotency | ✅ | PostgreSQL-backed key + request hash; same request replays original, changed body conflicts |
| Multi-seat partial behavior | ✅ | All-or-nothing in one PostgreSQL transaction |
| Cancellation | ✅ | Explicit owner-only cancellation; repeated cancellation is idempotent |
| Released seat re-bookable | ✅ | Cancellation and stale-cancellation/rebooking flow verified |
| Show state | ✅ | `GET /shows/{id}` exposes per-seat state and counts |
| Reconciliation invariant | ✅ implementation | `available + held + confirmed = total`; this implementation uses explicit cancellation, so `held = 0` |
| PostgreSQL correctness if Redis is unavailable | ✅ | Redis-down 500-way hot-seat test still produced 1 × 201, 499 × 409, 0 5xx |
| Liveness / readiness | ✅ | Live Render endpoints return UP |
| Structured logs + request ID | ✅ | JSON logs; generated and caller-supplied `X-Request-ID` verified |
| Prometheus metrics | ✅ base/custom metrics | Reservation and cancellation request/response/duration metrics verified |

## Concurrency and Load-Test Results

### Exact 500-way hot-seat contention

500 users simultaneously attempted the same fresh seat.

```text
201 Created:       1
409 Conflict:    499
5xx:               0
Network errors:    0
Unexpected:        0
```

This directly proves the core contention requirement: **exactly one winner and 499 clean declines**.

### Optimized 5,000 hot-seat run

```text
201 Created:        1
409 Conflict:   4,999
5xx:                0
Network errors:     0
Throughput:    ~113.7 attempts/sec
p95:             ~1.5 sec
```

### Per-user limit concurrency

One user fired 8 parallel reservations on a show with limit = 4.

```text
201 Created:     4
409 Conflict:    4
5xx:             0
Network errors:  0
```

The user never exceeded four confirmed seats.

### 5,000 cancellation retries

```text
200 OK:          5,000
401/403/404:         0
409:                 0
5xx:                 0
Network errors:      0
Throughput:      ~67.1 req/sec
p95:              ~2.34 sec
```

### Redis unavailable correctness test

Redis was intentionally stopped and 500 users raced for the same seat.

```text
201 Created:       1
409 Conflict:    499
5xx:               0
Network errors:    0
```

Database verification after the run found **0 duplicate confirmed seats**.

This demonstrates the design rule:

> Redis is a contention optimization; PostgreSQL is the correctness authority.

### 200,000-request experiment

A 200,000-request experiment is intentionally **not** claimed as a successful benchmark because one local application process was gracefully shut down during the run, causing connection-refused errors afterward. The result is documented for transparency rather than presented as a pass.

### 20,000-request burst

The repository includes:

```text
load-test/burst-20k.js
```

The script is configured for 20,000 reservation attempts and supports a public `BASE_URL`. A final clean 20k live run should be recorded before submission if the free-tier environment can sustain it.

## Load-Test Scripts

```text
load-test/hot-seat-500.js
load-test/per-user-limit.js
load-test/burst-20k.js
load-test/cancel-5000.js
```

Example live run:

```powershell
k6 run `
  -e BASE_URL="https://seat-reservation-service-2kyt.onrender.com" `
  -e SHOW_ID="<SHOW_ID>" `
  -e HOT_SEAT="A12" `
  -e USER_COUNT=500 `
  .\load-test\hot-seat-500.js
```

## Correctness Mechanism

The atomic decision lives in PostgreSQL:

1. Idempotency state is claimed and locked.
2. The user/show booking counter is locked.
3. Requested seat rows are acquired with pessimistic write locks in deterministic seat order.
4. Availability and per-user limits are revalidated inside the transaction.
5. All requested seats are confirmed atomically, or the entire transaction rolls back.

Redis/Redisson is used to reduce contention across instances, but every successful reservation still passes through the PostgreSQL correctness boundary.

## Observability

Verified:

- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/prometheus`
- JSON structured logs
- `X-Request-ID` generation and propagation
- custom reservation metrics
- custom cancellation metrics

Current custom metrics include:

```text
seat_reservation_requests_total
seat_reservation_responses_total
seat_reservation_duration_seconds
seat_cancellation_requests_total
seat_cancellation_responses_total
seat_cancellation_duration_seconds
```

## Final Submission Note

The strongest demonstrated correctness evidence is the exact same-seat contention test:

> **500 simultaneous buyers → exactly 1 confirmed reservation, 499 HTTP 409 declines, 0 HTTP 5xx.**


# Engineering Write-up

## Problem

The primary challenge in a seat reservation service is maintaining correctness when many clients attempt to reserve the same inventory simultaneously.

The system must guarantee that a seat cannot be sold twice while remaining responsive under contention.

## Source of Truth

PostgreSQL is the final source of truth.

Redis is deliberately not used as the final correctness boundary.

This means that even if Redis fails or a distributed lock expires unexpectedly, the PostgreSQL transaction still prevents an invalid reservation.

## PostgreSQL Locking

Requested seats are loaded using pessimistic write locking.

Conceptually:

```sql
SELECT ...
FROM seats
WHERE show_id = ?
AND seat_no IN (...)
FOR UPDATE;
```

The locked rows are then revalidated inside the transaction.

Only AVAILABLE seats can transition to CONFIRMED.

This protects the system across multiple application instances.

## Per-user Limit

A separate user/show booking counter is maintained.

The row is locked before evaluating the reservation request.

This prevents two concurrent requests by the same user from independently passing the booking-limit check.

For a limit of four:

```text
Request A sees 3 seats
Request B waits

Request A:
3 -> 4
commit

Request B:
sees 4
reject
```

Therefore the limit remains safe under concurrency.

## Atomic Multi-seat Reservation

All requested seats participate in the same database transaction.

The transaction either confirms every requested seat or none of them.

There is no partial reservation.

## Idempotency

Idempotency is persisted in PostgreSQL using:

```text
user_id
idempotency_key
request_hash
state
reservation_id
response_code
```

The request hash is calculated from the show identifier and normalized seat list.

This produces:

```text
same key + same request
=> replay original reservation

same key + different request
=> conflict
```

## Redis

Redis/Redisson provides distributed locks around:

```text
user/show
seat
idempotency key
```

These locks reduce unnecessary database contention when multiple application instances receive the same hot-seat traffic.

Redis is an optimization only.

## Bounded Redis Locking

The original implementation used blocking Redis locking.

Under extreme contention this produced long lock queues.

The optimized implementation uses:

```java
multiLock.tryLock(500, 60_000, TimeUnit.MILLISECONDS);
```

If the lock cannot be acquired quickly, the service does not remain indefinitely queued.

It either:

- rejects through the fast availability path, or
- falls back to PostgreSQL locking.

## Fast Rejection

Once a seat has already been confirmed, most later hot-seat requests do not need to enter the Redis queue or write transaction.

A read-only precheck detects already-unavailable seats.

This precheck is only allowed to reject a reservation.

It is never allowed to approve one.

Final approval always happens inside the PostgreSQL transaction.

Existing idempotency records bypass the fast rejection path so valid idempotent retries still work.

## Cancellation

Cancellation is explicit rather than expiration-based.

Therefore:

```text
held = 0
```

Cancellation locks the reservation and validates ownership.

For the first cancellation:

```text
CONFIRMED
->
CANCELLED
```

The related seats become AVAILABLE and the user's confirmed-seat count is decremented.

Subsequent cancellation requests return the already-cancelled reservation without performing the state mutation again.

This makes cancellation idempotent.

Historical reservation-seat rows are retained.

## Stale Cancellation Safety

A cancelled reservation's historical seat assignment must never be used to free a seat that has since been booked by another reservation.

The cancellation transaction validates ownership of the currently assigned seat before releasing it.

Repeated cancellation of an old reservation therefore cannot release another user's newly booked seat.

## Redis Failure

The service catches Redis failures and executes the PostgreSQL transaction directly.

This intentionally preserves the architecture rule:

> Redis improves contention handling; PostgreSQL guarantees correctness.

Readiness therefore depends on PostgreSQL rather than Redis.

## Observability

Spring Boot Actuator exposes:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/prometheus
```

Custom Micrometer metrics record reservation and cancellation request outcomes and durations.

The service also generates or propagates:

```text
X-Request-ID
```

for request correlation.

The request ID is placed into SLF4J MDC and included in structured JSON logs.

## Load Testing

### 5,000 hot-seat reservations

Observed:

```text
1 × 201
4,999 × 409
0 × 5xx
0 network failures
```

The optimized run achieved approximately:

```text
113.7 reservation attempts/sec
p95 ≈ 1.5 sec
```

### 5,000 concurrent cancellation retries

Observed:

```text
5,000 × 200
0 × 409
0 × 5xx
0 network failures
```

Performance:

```text
~67.1 requests/sec
p95 ≈ 2.34 sec
```

The earlier implementation using blocking Redis cancellation locks produced request timeouts.

Bounded locking removed those timeouts.

## 200k Experiment

A 200,000-request local experiment completed all k6 iterations, but one local application process received a graceful shutdown signal during execution.

Because approximately 26,937 requests subsequently encountered connection-refused errors, that experiment is not presented as a successful performance benchmark.

This distinction is intentional: infrastructure failure should not be presented as a successful application benchmark.

## Production Improvements

With additional production requirements I would consider:

- managed PostgreSQL
- managed Redis
- centralized logs
- Grafana dashboards
- OpenTelemetry tracing
- rate limiting
- connection-pool tuning
- autoscaling
- external load balancing
- alerting on conflict/error/latency rates
- deployment health gates
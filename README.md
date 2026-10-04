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
# Final Validation Tests

## 1. 500 simultaneous hot-seat reservations

Pick an AVAILABLE seat.

```powershell
$showId = "7b70920a-27cb-4d50-9cad-e9ff81b5d0f9"
$hotSeat = "A9"
```

Run:

```powershell
k6 run `
    -e SHOW_ID=$showId `
    -e HOT_SEAT=$hotSeat `
    -e USER_COUNT=500 `
    .\load-test\hot-seat-500.js
```

Expected:

```text
hot500_201 = 1
hot500_409 = 499
hot500_5xx = 0
hot500_network_error = 0
```

---

## 2. Per-user limit concurrency

Create an isolated show:

```powershell
$limitShowId = [guid]::NewGuid().ToString()

docker exec seat-postgres psql -U seatuser -d seatdb -c "INSERT INTO shows(id,name,price_paise,per_user_limit,total_seats) VALUES ('$limitShowId','limit-test',25000,4,8); INSERT INTO seats(show_id,seat_no,status) SELECT '$limitShowId','L'||g,'AVAILABLE' FROM generate_series(1,8) g;"
```

Run:

```powershell
k6 run `
    -e SHOW_ID=$limitShowId `
    -e SEATS="L1,L2,L3,L4,L5,L6,L7,L8" `
    .\load-test\per-user-limit.js
```

Expected:

```text
limit_201 = 4
limit_409 = 4
```

Verify:

```powershell
docker exec seat-postgres psql -U seatuser -d seatdb -c "SELECT usb.show_id,u.username,usb.confirmed_seats FROM user_show_booking usb JOIN users u ON u.id::text=usb.user_id WHERE usb.show_id='$limitShowId';"
```

Expected:

```text
confirmed_seats = 4
```

---

## 3. Redis-down correctness

Use a fresh seat.

Stop Redis:

```powershell
docker stop seat-redis
```

Check readiness:

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/health/readiness"
Invoke-RestMethod "http://localhost:8081/actuator/health/readiness"
```

Run:

```powershell
k6 run `
    -e SHOW_ID=$showId `
    -e HOT_SEAT=$hotSeat `
    -e USER_COUNT=500 `
    .\load-test\hot-seat-500.js
```

Expected:

```text
1 × 201
499 × 409
0 × 5xx
0 network errors
```

Restore:

```powershell
docker start seat-redis
docker exec seat-redis redis-cli ping
```

Expected:

```text
PONG
```

---

## 4. Double-selling database validation

```powershell
docker exec seat-postgres psql -U seatuser -d seatdb -c "SELECT rs.show_id,rs.seat_no,COUNT(*) AS confirmed_reservations FROM reservation_seats rs JOIN reservations r ON r.id=rs.reservation_id WHERE r.status='CONFIRMED' GROUP BY rs.show_id,rs.seat_no HAVING COUNT(*)>1;"
```

Expected:

```text
0 rows
```

---

## 5. 20,000 burst

Use another fresh seat.

```powershell
k6 run `
    -e SHOW_ID=$showId `
    -e HOT_SEAT=$hotSeat `
    -e USER_COUNT=100 `
    .\load-test\burst-20k.js
```

Expected:

```text
burst_201 = 1
burst_409 = 19999
burst_5xx = 0
burst_network_error = 0
```

---

## 6. Prometheus

```powershell
Invoke-WebRequest "http://localhost:8080/actuator/prometheus" | Select-String "seat_"
```

Expected metric families include:

```text
seat_reservation_requests
seat_reservation_responses
seat_reservation_duration
seat_cancellation_requests
seat_cancellation_responses
seat_cancellation_duration
```

---

## 7. Request ID

Generated ID:

```powershell
$response = Invoke-WebRequest "http://localhost:8080/actuator/health"
$response.Headers["X-Request-ID"]
```

Expected:

```text
UUID
```

Propagation:

```powershell
$response = Invoke-WebRequest "http://localhost:8080/actuator/health" -Headers @{"X-Request-ID"="takehome-test-123"}
$response.Headers["X-Request-ID"]
```

Expected:

```text
takehome-test-123
```

---

## 8. Cancellation

Run existing cancellation test:

```powershell
k6 run `
    -e RESERVATION_ID=$reservationId `
    -e USERNAME=$winnerUsername `
    -e PASSWORD="LoadTest@123" `
    -e TOTAL_REQUESTS=5000 `
    -e VUS=100 `
    .\load-test\cancel-5000.js
```

Expected:

```text
cancellation_200 = 5000
cancellation_5xx = 0
cancellation_network_error = 0
```
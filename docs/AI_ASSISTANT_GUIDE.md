# AI Seat Operations Assistant

## Purpose

This branch adds an agentic AI layer to the existing seat-reservation service.

The seat-reservation domain is used as an interview-safe analogy for an IoT operations assistant:

| IoT concept | Seat-reservation equivalent |
|---|---|
| Device | Seat |
| Device/fleet state | Show + seat inventory |
| ONLINE/OFFLINE | AVAILABLE/CONFIRMED |
| Device lookup | Show/seat lookup |
| Device operation | Reserve/cancel operation |
| Backend microservice tool | Existing Spring service method |
| Authenticated operator | Authenticated reservation user |

The LLM is **not** the correctness boundary. Existing Java services, JWT identity, Redis contention control, PostgreSQL transactions, authorization, and idempotency remain authoritative.

## Architecture

```text
Authenticated user
      |
      v
POST /assistant/chat
      |
      v
AssistantController
      |
      | JWT subject -> ToolContext
      v
SeatReservationAssistantService
      |
      v
Spring AI ChatClient
      |
      v
LLM chooses approved tools
      |
      +-------------------+-------------------+
      |                   |                   |
      v                   v                   v
getSeatStatus        getShowState        reserveSeats
                                             |
                                             v
                                  ReservationLockService
                                             |
                                  Redis + PostgreSQL
```

## Tools

- `getShowState(showId)` — read the whole show and seat counts.
- `getSeatStatus(showId, seatNumber)` — read one seat state.
- `getMyReservation(reservationId)` — read only the authenticated user's reservation.
- `reserveSeats(showId, seats)` — reserve seats using the existing locking/idempotency flow.
- `cancelMyReservation(reservationId)` — cancel only the authenticated user's reservation.

## Security design

The model never receives a model-controlled `userId`.

The controller reads `jwt.getSubject()` and puts the authenticated user ID into Spring AI `ToolContext`. Tool implementations use that server-supplied identity for reservation and cancellation operations.

This preserves the same security rule as the REST API:

> identity comes from authentication, not from user text or model output.

## Conversation memory

The assistant uses a bounded message window and a caller-provided `conversationId`.

Example:

```text
User: For show <SHOW_ID>, is A12 available?
Assistant: A12 is available.

User: Book it.
Assistant: [uses conversation context to resolve "it" as A12]
```

## Enable locally

AI is disabled by default.

PowerShell:

```powershell
$env:AI_ENABLED="true"
$env:AI_MODEL_CHAT="openai"
$env:OPENAI_API_KEY="<your-api-key>"
$env:OPENAI_MODEL="gpt-5-mini"
```

Then start the application normally.

## Endpoint

```http
POST /assistant/chat
Authorization: Bearer <JWT>
Content-Type: application/json
```

Example:

```json
{
  "message": "For show 11111111-1111-1111-1111-111111111111, is seat A12 available?",
  "conversationId": "demo-1",
  "requestId": "request-1"
}
```

Follow-up:

```json
{
  "message": "Book it for me",
  "conversationId": "demo-1",
  "requestId": "request-2"
}
```

## Interview explanation

> I added an agentic orchestration layer on top of an existing Spring Boot reservation platform. The LLM interprets natural-language intent and selects from a small allow-listed set of tools. The tools call the same backend services used by the REST API, so Redis locking, PostgreSQL transactions, per-user limits, authorization, and idempotency are preserved. Authenticated identity is injected through ToolContext rather than being model-controlled. Conversation memory supports multi-turn requests such as checking a seat and then saying "book it."

## Design boundary

```text
LLM          = intent interpretation + tool selection + response generation
Spring/Java  = authentication + validation + authorization + tool execution
Redis        = contention optimization
PostgreSQL   = final correctness authority
```

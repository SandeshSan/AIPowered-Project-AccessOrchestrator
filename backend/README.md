# Access Orchestrator – Backend (Phase 1)

Spring Boot 3.5 / Java 21 backend. The access gap is computed deterministically in
`AccessAnalysisService` (missing = required − existing). The AI layer will only explain it.

## Build & test

Requires JDK 21 and Maven 3.9+. Tests use in-memory H2, so no database is needed.

```bash
mvn clean verify
```

## Run locally

```bash
mvn spring-boot:run
```

The database is **in-memory H2** by default, recreated and re-seeded on every start. PostgreSQL still works:
start it with `docker compose up -d` (this folder) and set `DB_URL=jdbc:postgresql://localhost:5432/access_orchestrator`,
`DB_USERNAME=orchestrator`, `DB_PASSWORD=orchestrator`.

Start the [Mock IGA Service](../mock-iga-service/README.md) first; submitting requests calls it.

Environment overrides: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `SEED_DEMO_DATA` (default `true`),
`IGA_PROVIDER` (`mock-rest` | `in-memory`), `MOCK_IGA_URL` (default `http://localhost:8081`),
`IGA_POLLING_ENABLED` (default `true`), `IGA_POLLING_INTERVAL` (default `3s`).

## Sign-in (HTTP Basic)

Every `/api/**` call needs `Authorization: Basic base64(userId:password)` (Spring Security, stateless, no session
cookie). Unknown users, wrong passwords, users without a password and INACTIVE users get **401** as a
ProblemDetail, deliberately without `WWW-Authenticate` so browsers show the app's login page instead of their own
dialog. `/actuator/health` stays open. The principal is the canonical user ID (`CurrentUser.id()`); the user ID
is matched case-insensitively. Passwords are stored BCrypt-hashed in `app_user.password_hash`.

Demo users: the seeder gives every user without a password the value of `app.security.demo-password`
(`DEMO_PASSWORD`, default in `application.yml`), including databases seeded before sign-in existed. Change it
for anything shared.

```bash
curl -u NT10020:<demo password> http://localhost:8080/api/me
```

## AI Access Agent

`POST /api/agent/chat` with `{"message": "...", "conversationId": "<from previous reply, optional>"}`.
Signed-in user = the Basic-auth principal.

Configure an OpenAI-compatible model with `OPENAI_API_KEY` (required), `OPENAI_BASE_URL`, `OPENAI_MODEL`
(default `gpt-4o-mini`). Without a key the app still runs and the endpoint answers 503.

```bash
curl -s -X POST localhost:8080/api/agent/chat -H "Content-Type: application/json" -d '{"message":"I just joined the Novatech project. Can you get me the access I need?"}'
```

The reply contains `reply` (text), `awaitingConfirmation`, `analysis` (structured gap), `accessRequest` (when
one was created) and `toolCalls`. Answer "Yes" with the same `conversationId` to submit.

Tools (`AccessAgentTools`): getCurrentUser, getUser, findProject, getRequiredProjectAccess, getUserExistingAccess,
calculateMissingAccess, getEntitlementDetails, createAccessRequest, getAccessRequestStatus. All delegate to the
Java services; the LLM never computes the gap.

Guardrails enforced in code, not only in the prompt:
- Access data and requests are limited to the signed-in user (identity travels in the Spring AI ToolContext).
- `createAccessRequest` only succeeds if the system showed exactly those entitlements as requestable in an
  earlier turn **and** the user's current message is an explicit confirmation ("yes", "go ahead", ...).
  "No" withdraws the offer.
- The service re-validates everything: user and project active, entitlement ids exist and belong to the project
  profile, not already held, no request already in progress for them.

## Viewing a reportee's access

"What access does Asha have?" — the assistant's `getUserExistingAccess` works for yourself, for admins, and for line
managers looking at people up to 3 levels below them; anyone else is refused (`AccessAuthority.requireCanViewAccess`).
`GET /api/team` returns the signed-in user's reportees with level, projects and active access (the UI's **My Team**
page, shown only to people with reportees). Read-only.

## Adding someone to a project (via the assistant)

"Add John to Atlas" — admins, or managers for people up to 3 levels below them. Tools: `previewAddToProject`,
`addEmployeeToProject`. Only the **membership** is created (a previously removed membership is re-activated); **no
access is requested from the IGA**. The preview lists the access the person already has and what they will still need
to request themselves. Same guardrails: preview in an earlier turn, then explicit confirmation (typed, or the UI's
"Confirm & add" button, sent as `confirmAddition`).

## Removing someone from a project (via the assistant)

"Remove Asha from Novatech" (her manager) or "I'm leaving Novatech" (anyone). Tools: `findEmployee`,
`getMyTeam`, `getProjectMembers` (admins only), `previewProjectRemoval`, `removeEmployeeFromProject`.

- **Who:** anyone may remove themselves; admins may remove anyone; a line manager may remove people up to
  **3 levels** below them in the reporting line (`app_user.manager_id`), from any of their projects. Rules live in
  `AccessAuthority`. Seeded reporting line: Grace (admin) ← Mei ← John, Asha, Marco, Elena, Priya; Priya ← David.
- **What is revoked:** the person's ACTIVE access that is in the project's access profile, **except DEFAULT
  (default) access**, which is kept. Computed in Java (`RevocationCalculator`).
- **Flow:** preview → user confirms with a reason (required) → membership ends (kept for audit: when, by whom, why)
  → the person's pending grant requests for the project are cancelled → a **REVOKE** request goes to the IGA, which
  needs **approval** → on completion the access rows become REVOKED.
- Same guardrails as access requests: the preview must be shown in an earlier turn and the user must confirm.
  The UI's "Confirm removal" button sends the reason as a separate `removalReason` field.

## API

| Method | Path | Purpose |
|---|---|---|
| GET  | `/api/users/{userId}` | User profile |
| GET  | `/api/users/{userId}/access` | User's ACTIVE entitlements |
| GET  | `/api/projects` | All projects |
| GET  | `/api/projects/{projectId}` | One project |
| GET  | `/api/projects/{projectId}/access` | Project standard access profile (required + optional) |
| GET  | `/api/projects/{projectId}/required-access` | Mandatory entitlements only |
| GET  | `/api/access/missing?userId=&projectId=` | Required − existing |
| POST | `/api/access/analyze` | `{userId, projectId}` → required / existing / missing |
| POST | `/api/access/compare` | `{userId, projectId}` → per-entitlement GRANTED/MISSING, summary, additional access, verification |
| POST | `/api/access/requests` | Create DRAFT request for missing access (optional `entitlementCodes` subset) |
| GET  | `/api/access/requests?userId=` | A user's requests, newest first |
| GET  | `/api/access/requests/{requestId}` | Read a request, including `lifecycle` stepper stages |
| POST | `/api/access/requests/{requestId}/submit` | User confirmation: send DRAFT to the IGA |
| POST | `/api/access/requests/{requestId}/sync` | Pull status from the IGA now (a poller also does this) |
| POST | `/api/agent/chat` | AI Access Agent (see above) |

Errors are RFC 9457 `ProblemDetail`: 400 validation, 404 not found, 409 concurrent update, 422 business rule,
502 IGA or AI provider unavailable, 503 AI agent not configured.

Access is recorded as ACTIVE (source `IGA`) only when the IGA reports PROVISIONED; revoked/expired rows are
reactivated. Submission re-checks the gap, so access granted after a draft was created is never re-requested.

Demo (Novatech):
- `NT10036` John → missing `NOVATECH_DB_READ`, `NOVATECH_JIRA`, `NOVATECH_VPN` (5 required − 2 granted = 3 missing)
- `NT10042` Asha → missing `NOVATECH_DB_READ` (expired), `NOVATECH_VPN` (revoked); `ORION_DEV` is additional access

More demo users (sign in as them):

| User | Scenario |
|---|---|
| `NT10051` Priya | Atlas Payments 100% (with a past PROVISIONED request); Helios Mobile missing Firebase, App Store, Figma |
| `NT10058` Marco | New on Helios with no access (0%); a past App Store request was REJECTED |
| `NT10063` Elena | Orion 100%; access from the CLOSED Zephyr project is revoked |
| `NT10070` David | INACTIVE contractor on Atlas; any request for him is refused |
| `NT10001` Grace | Access Administrator: the only **admin**; can browse the project catalog |

The project catalog (`/api/projects/**`, the UI's Projects page) is **admin-only**: other users get 403 and the UI
hides the page. Employees still see their own projects on the dashboard and through the Access Assistant.
(Identity comes from HTTP Basic sign-in; production would use SSO.)

`CORP_VPN` is one entitlement required by both Atlas and Helios. Zephyr Data Migration is a CLOSED project
(it can be viewed, but not requested).

Only ACTIVE user access counts as existing. Optional (`required=false`) profile entries never count as missing.

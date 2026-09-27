# Mock IGA Service

Simulates the external Identity Governance & Administration system the orchestrator integrates with.
In-memory store (data resets on restart); no authentication. Port 8081.

Manager console: **http://localhost:8081/** lists requests, supports Approve / Reject (provisioning then runs automatically), and shows
the lifecycle stepper and audit trail, refreshing every second.

## Lifecycle

```
Request Created -> Pending Approval -> Approved -> Provisioning -> Provisioned
                                   \-> Rejected
```

Illegal transitions return **409** (e.g. provision before approval, approve twice, anything after reject).

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/mock-iga/access-requests` | `{userId, entitlements[], externalReference?, requestedBy?, justification?}` → `201 {requestId, status}` |
| GET  | `/mock-iga/access-requests?status=` | List, newest first |
| GET  | `/mock-iga/access-requests/{requestId}` | Detail with `history` and `lifecycle` stages |
| POST | `/mock-iga/access-requests/{requestId}/approve` | Optional `{actor, comment}` |
| POST | `/mock-iga/access-requests/{requestId}/reject` | Optional `{actor, comment}` |
| POST | `/mock-iga/access-requests/{requestId}/provision` | APPROVED → PROVISIONING → (delay) → PROVISIONED |

## Configuration

| Property | Env var | Default |
|---|---|---|
| `mock-iga.provisioning.auto-provision-on-approve` | `MOCK_IGA_AUTO_PROVISION` | `true` (approval alone drives provisioning) |
| `mock-iga.provisioning.start-delay` | `MOCK_IGA_START_DELAY` | `2s` (time spent in APPROVED, auto mode) |
| `mock-iga.provisioning.duration` | `MOCK_IGA_PROVISION_DURATION` | `3s` (time spent in PROVISIONING) |
| `mock-iga.request-id-start` | – | `10145` |

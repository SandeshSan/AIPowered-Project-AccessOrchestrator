# AI Powered Project Access Orchestrator (POC)

| Module | Port | Purpose |
|---|---|---|
| [`frontend/`](frontend/README.md) | 4200 | Angular UI: Dashboard, Access Assistant, My Access, Access Requests, My Team (managers), Projects (admins) |
| [`backend/`](backend/README.md) | 8080 | Access analysis (required − existing = missing), AI Access Agent, access requests, IGA integration |
| [`mock-iga-service/`](mock-iga-service/README.md) | 8081 | **Mock IGA Service**: simulates the external IGA (approval + provisioning authority) |

AI = understanding and explanation · application code = business rules · IGA = approval and provisioning.
The backend never grants access itself: it records access as ACTIVE only after the IGA reports PROVISIONED.

## Build everything

Requires JDK 21, Maven 3.9+ and Node 18.13+.

```bash
mvn clean verify
```

```bash
cd frontend && npm install && npm run test:ci && npm run build
```

## Run with Docker

Each service has its own multi-stage `Dockerfile` (Maven/Node build stage, slim non-root runtime with a
health check). The root `docker-compose.yml` runs the whole stack. The backend uses an in-memory **H2** database,
so the demo data is seeded afresh every time the backend container starts:

```bash
cp .env.example .env        # add OPENAI_API_KEY (without it the assistant answers 503)
docker compose up --build
```

| Service | Image | URL |
|---|---|---|
| frontend (nginx, proxies `/api` to the backend) | `access-orchestrator-frontend` | http://localhost:4200 |
| backend | `access-orchestrator-backend` | http://localhost:8080 |
| mock-iga (manager console) | `mock-iga-service` | http://localhost:8081 |

`docker compose restart backend` resets the demo data.
The frontend image takes `BACKEND_URL` (default `http://backend:8080`) to run against another backend.

## Run the demo

```bash
java -jar mock-iga-service/target/mock-iga-service-0.1.0-SNAPSHOT.jar   # http://localhost:8081 = manager console
OPENAI_API_KEY=... java -jar backend/target/access-orchestrator-0.1.0-SNAPSHOT.jar
cd frontend && npm start                                               # http://localhost:4200
```

Sign in at http://localhost:4200 with a demo employee ID (e.g. `NT10036` John, `NT10020` Mei the manager,
`NT10001` Grace the admin) and the demo password (`app.security.demo-password` in
`backend/src/main/resources/application.yml`, overridable with `DEMO_PASSWORD`).

Flow: ask the Access Assistant "I just joined the Novatech project. Can you get me the access I need?", confirm
with "Yes", approve the request in the Mock IGA console, and watch it reach Provisioned on the Access Requests page.

# Access Orchestrator – Angular UI

Angular 17 (standalone components, signals) + Angular Material 17. Requires Node 18.13+.

## Run

Start the Mock IGA Service (8081) and the backend (8080) first, then:

```bash
npm install
npm start
```

Open http://localhost:4200. `/api` is proxied to `http://localhost:8080` (`proxy.conf.json`).

## Pages

| Page | What it shows |
|---|---|
| Dashboard | Active Access, Pending Requests, Projects, Missing Access cards; my projects with coverage; pending requests with lifecycle; active entitlements; recently provisioned |
| Access Assistant | Chat with the AI agent: gap analysis card, Yes/No confirmation, submitted-request card, tools used |
| My Access | Sortable, filterable table of active entitlements |
| Access Requests | Every request with a live lifecycle stepper (polls every 3 s while in progress), items, refresh / submit actions |
| My Team | Managers only: each reportee (up to 3 levels) with projects and active access; filterable |
| Projects | All projects with your coverage; project detail compares the standard access profile with your access |

**Sign in** (`/login`) with employee ID and password. The app checks them with `GET /api/me` over HTTP Basic,
then keeps the credential in `sessionStorage` (survives a reload, gone when the tab closes) and the interceptor
sends it on every API call. All other pages require sign-in (`authGuard`, with a return URL). A 401 later signs
you out and returns you to the login page. The user menu (top right) shows who you are and has **Sign out**,
which also forgets the assistant conversations. Demo credentials: see the backend README.

## Test and build

```bash
npm run test:ci
npm run build
```

`test:ci` runs Karma headless. Without Chrome, point `CHROME_BIN` at Edge (Chromium), e.g.
`CHROME_BIN="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"`.

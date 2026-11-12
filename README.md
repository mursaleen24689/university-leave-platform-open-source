# University Leave Platform

Leave management for a whole institution: one place where staff apply, heads of
department approve, administrators set the rules, and every decision leaves a
trace that can be read back years later.

**Status:** a working application, not a hosted service. Everything described
here runs: `611` unit tests pass with `./mvnw verify`, the migrations apply to an
empty Oracle schema in order, and the packaged JAR starts against a fresh schema
and signs in as the administrator its bootstrap variables create. What is not
here is a production server, so the build stays `0.0.1-SNAPSHOT`, and the
organization it loads is invented sample data, not a real university's.

## What it does

- **Apply and approve.** A member of staff asks for leave, one or several days at
  a time; it waits for their supervisor or head of department, who approves or
  rejects it. The applicant can withdraw it while it is still waiting, and
  balances move only when a decision is made.
- **Rules that hold.** Each leave type has an annual quota, a carrying-over rule
  and a compensatory rule. One request may not straddle two leave years, so a
  year-end application is two applications, and two approvals opened at the same
  moment cannot spend the same day twice.
- **Delegated approving.** A head of department who is away hands their approvals
  to someone else for a dated period; the hand-over is itself audited.
- **Reporting.** Leave register, per-department summary, monthly attendance
  sheet, a printable ISO-style application form with a QR verification code, a
  per-employee leave record, and CSV exports.
- **Administration.** Faculties, departments and offices; employees; bulk import
  from a file; sign-in accounts with roles and status; the leave-type and
  holiday calendars; record corrections; and outgoing mail settings.
- **Help desk and notifications.** Staff raise tickets, answers arrive in the
  header bell, and the same events can be emailed.
- **Audit trail.** Every privileged action writes an event with who, when, what,
  and the note they gave. Read-only accounts exist so it can be inspected
  without giving anyone the power to change anything.

## How it is built

| Piece | What the project uses |
| --- | --- |
| Language and runtime | Java 21 |
| Application | Spring Boot 4.1.2, Spring Security 7.1.2 |
| User interface | Vaadin 25.3.4 — the screens are Java on the server, no separate JavaScript app |
| Database | Oracle Database Free (23ai/26ai) with Flyway migrations `V001`–`V020` |
| Build | Maven wrapper (`backend/mvnw`), nothing to install separately |
| Tests | JUnit 5 + Mockito + AssertJ, 611 tests in 57 classes, no database needed |

The same work is reachable as JSON under one session cookie — `/api/v1/auth`,
`/api/v1/leave`, `/api/v1/delegations`, `/api/v1/profile`, `/api/v1/tickets`,
`/api/v1/notifications`, `/api/v1/departments`, `/api/v1/reports`,
`/api/v1/audit`, `/api/v1/admin` and its `delegations`, `profile-requests`,
`tickets` and `mail` sections. There is no OpenAPI document; the controllers are
the description and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) says what each
module owns.

Two paths are open on purpose: `GET /api/v1/auth/captcha` (the sign-in picture)
and `GET /api/v1/reports/verify` (check a printed form by its number). Both are
rate-limited per address.

## Screens

Thirteen routes: the sign-in page (`/login`) and twelve behind it. The start page
offers one card per screen the signed-in person may use, so an administrator sees
more cards than a member of staff with no approving role.

| Card | Route | Who sees it |
| --- | --- | --- |
| My leave | `/leave` | Everyone |
| Approvals | `/approvals` | Supervisor, head of department, administrator |
| Delegated approvals | `/delegations` | Supervisor, head of department, administrator |
| Administration | `/admin` | Administrator |
| Leave setup | `/leave-config` | Administrator |
| Audit trail | `/audit` | Administrator, read-only auditor |
| My staff record | `/profile` | Everyone |
| Change password | `/change-password` | Everyone |
| Two-step signing | `/two-step` | Everyone |

**Reports** (`/reports`) and **Help desk** (`/tickets`) are reached from the
header. The Administration screen holds the organization, employee, bulk-import,
account, mail, help-desk and record-correction cards;
[`docs/ADMIN-GUIDE.md`](docs/ADMIN-GUIDE.md) walks through each one.

## Sign-in security

The platform takes sign-in seriously, because a leave system knows who is away
and where:

- a server-drawn picture code every sign-in must answer, with per-address and
  per-session ceilings;
- optional authenticator-app two-step, whose seeds are encrypted at rest with a
  key that never enters the database;
- forced password change on first use, password history, and a lockout after
  repeated wrong passwords that releases itself rather than locking a person out
  forever;
- an account still on its issued temporary password can do nothing but change it
  — enforced on the screens and on every API path, which is where the first
  version of this rule had a hole;
- filters that end a session the moment an account is disabled, plus a
  whole-session absolute expiry.

[`docs/SECURITY.md`](docs/SECURITY.md) is the design write-up and ends with the
risks this version accepts. To report a suspected problem, see
[`SECURITY.md`](SECURITY.md) at the repository root.

## Try it

You need JDK 21, Node.js 24 or newer, and an Oracle Database Free instance with a
schema for the application. [`INSTALL.md`](INSTALL.md) covers all three,
including a five-minute Docker route for the database. Short version:

```bash
cp .env.example .env          # fill in the database values, keep .env out of git
set -a; . ./.env; set +a
cd backend && ./mvnw spring-boot:run
```

Then open <http://localhost:8080/>. The first start can take a few minutes while
Vaadin builds its front-end bundle. Flyway applies the migrations on first start,
so the database must exist before the application does — and the first
administrator must be created with the `LEAVE_BOOTSTRAP_ADMIN_*` values, because
the migrations load reference data only.

To see it work without a database, run the tests instead:

```bash
cd backend && ./mvnw verify
```

## Repository layout

| Path | Purpose |
| --- | --- |
| `backend/` | the application: Spring Boot services, Vaadin screens, Flyway migrations |
| `frontend/` | reserved — the interface is built in Java under `backend/` |
| `database/` | schema provisioning SQL and notes |
| `docs/` | architecture, leave rules, security, database, administration, operations, testing |
| `ops/` | server and deployment assets; the directory holds its README only |
| `tools/` | one-off scripts; the directory holds its README only |

## Documentation

| Read this | For |
| --- | --- |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | how the modules fit together, and the request filter chain |
| [`docs/LEAVE-RULES.md`](docs/LEAVE-RULES.md) | quotas, carry-over, compensatory leave, how a request is decided |
| [`docs/DATABASE.md`](docs/DATABASE.md) | the schema, the migrations, backup and restore on Oracle |
| [`docs/SECURITY.md`](docs/SECURITY.md) | the security design and its limits |
| [`docs/ADMIN-GUIDE.md`](docs/ADMIN-GUIDE.md) | day-to-day administration, screen by screen |
| [`docs/OPERATIONS.md`](docs/OPERATIONS.md) | monitoring, incidents, patching, scaling |
| [`docs/TEST-PLAN.md`](docs/TEST-PLAN.md) | what the suite covers and what it does not |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | why each choice was made, one entry per decision |
| [`docs/GLOSSARY.md`](docs/GLOSSARY.md) | the words the screens use, defined once |
| [`INSTALL.md`](INSTALL.md) · [`CONTRIBUTING.md`](CONTRIBUTING.md) · [`CHANGELOG.md`](CHANGELOG.md) | setting it up, changing it, what moved |

## Data this copy carries

No real institution, no real person, no credential. The faculties, departments
and employee numbers that `V006` loads are invented for the sample, the logo and
stylesheet are placeholders, and every secret in `.env.example` is empty. If you
point this at your own organization, replace `V006` with your structure — it is
the only migration that holds names rather than rules.

## Licence

Released under the MIT licence — see [`LICENSE`](LICENSE).

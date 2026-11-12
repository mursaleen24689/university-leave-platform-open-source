# Changelog

Changes are kept here in [Keep a Changelog](https://keepachangelog.com/) format
and follow [Semantic Versioning](https://semver.org/).

This is the public history. It starts at the release, not at the first commit:
the development that came before was for one institution, and its tickets,
screenshots and data are not part of this repository.

## [Unreleased]

Work on `main` after the `0.1.0` tag, not yet released.

### Added

- **`V020__index_balances_by_type_and_year.sql`** gives `leave_balance` the index
  the year-end reports ask for, `(leave_type_id, leave_year)`. The only key that
  reached those columns led with the employee, which is right for "my balance" and
  wrong for "every balance of casual leave in 2026" - that read the table in full.
  Measured on a throwaway schema carrying 4,000 staff, six leave types and three
  years: `TABLE ACCESS FULL` became `INDEX RANGE SCAN`. The index the same review
  suggested for `leave_application (leave_type_id, status)` was measured too and
  refused, because no query in the application names a leave type on that table.

### Fixed

- **Pressing *Import rows* twice no longer files the same staff sheet twice.** The
  import runs on the request thread, so a large sheet leaves the screen silent for
  a second or more, and the natural second click re-submitted what was already
  being written. The button now goes dead for as long as its own click is being
  served. The import itself is still synchronous and still shows no progress.
- **`docs/SECURITY.md` claimed an unknown username and a wrong password take
  different amounts of time to refuse.** They do not, and the section now says so
  with the numbers: 73 ms against 69 ms. `DaoAuthenticationProvider` compares the
  typed password against a hash of its own when the account is not found, so the
  gap the document described was already closed by the framework - and the dummy
  comparison it recommended would have reopened it, on the other side.

## [0.1.0] - 2026-11-12

The first public release. Everything below is what the code in this tag does.

### Added

- **Leave workflow** — apply, approve, reject, withdraw, and compensatory leave
  against a per-type annual quota with its own carry-over rule. Balances move
  only when a decision is made.
- **Two-level approval** with a delegated (acting) approver for a dated period;
  the delegation and its end are audited.
- **Organization administration** — faculties, departments and offices,
  employees, bulk import from CSV, and sign-in accounts with roles.
- **Leave setup** — leave types, quotas and the holiday calendar.
- **Reports** — leave register, per-department summary, monthly attendance
  sheet, printable ISO-style application form with a QR verification code,
  per-employee leave record, and CSV exports.
- **An anonymous form check** at `GET /api/v1/reports/verify`, rate-limited per
  address, so a printed sheet can be confirmed from its number.
- **Help desk tickets** with an administration view, and **in-app
  notifications** in the header bell; the same events can be mailed through
  SMTP settings held in the database with the password sealed by an
  environment key.
- **Profile-change requests** that an administrator approves before the staff
  record changes.
- **Audit trail** for privileged actions, with a read-only auditor role.
- **Sign-in security** — server-drawn picture code with per-address and
  per-session ceilings, forced first password change, password history,
  expiring lockout after five wrong passwords, and optional authenticator-app
  two-step whose seeds are encrypted with their own key.
- **JSON API** under `/api/v1/**` sharing the same session, roles and rules as
  the screens.
- 611 unit tests in 57 classes that need no database, no browser and no Spring
  context; `docs/TEST-PLAN.md` says what they do not cover.
- Maven `production` profile that puts the Vaadin bundle inside the JAR, the
  Maven wrapper, and `.env.example` documenting every environment variable.

### Changed

- The starter organization (`V006`) invents faculties, departments and offices
  rather than naming a real institution, and the logo, stylesheet and footer are
  placeholders. Point it at your own structure before use.
- Secrets move to the environment: `LEAVE_MAIL_KEY`, `LEAVE_VERIFY_KEY` and
  `LEAVE_TWOSTEP_KEY` never enter the database, and each feature that needs one
  disables itself rather than store something it cannot protect.

### Fixed

Found in the security and logic review done for this release. Each was a real
defect in the code that came before it.

- An account still on its issued temporary password could use the JSON API.
  `/api/**` now answers `403` until the password is changed, which is what the
  screens already required.
- A request whose days fell in two calendar years charged every one of them to
  the starting year, so the following year's balance was never touched and a
  person with a full new year was refused in late December. It is refused now,
  with the sentence naming both halves, and the date pickers stop it before the
  form is sent.
- Two requests arriving at the same moment for an employee with no balance row
  both found nothing to lock — `SELECT ... FOR UPDATE` matching no row locks
  nothing in Oracle — and one write was lost on a constraint violation. The
  employee row is locked first, and one order holds everywhere: application,
  employee, balance.
- The supervisor check only refused the obvious case, a person supervising
  themselves; a chain of three that looped back was accepted, and a request in
  that loop escalates to the person who raised it forever. Both doors are
  closed — the screen, and the bulk import, which names the row it stopped on.
- The staff import read every column of a row and wrote every column, so a
  sheet with a blank cell cleared what the platform already knew about that
  person. A blank cell now means "not supplied", except in the supervisor
  column, where blank is a real answer.
- The printed leave record counted calendar days and subtracted only holidays,
  so Friday plus Monday looked like four days, with the weekend claimed as
  leave. It works from the working days the request actually took.
- The CSV formula guard missed a value starting with a tab or a carriage
  return, which is what a spreadsheet strips before deciding a cell is a
  formula. Those are quoted now too.
- Reference numbers were zero-padded by a trigger that truncated anything past
  six digits, so request one-million-and-one printed as the same text as
  request one hundred thousand, and the unique index refused the insert.
  `V019` grows the padding instead.
- `X-Forwarded-For` was believed from anybody, so a script could refill its
  captcha allowance by rotating the header. Forwarded headers now go through
  Tomcat, which accepts them only from a proxy on an internal address.
- A session lived as long as it kept being used. There is now an absolute
  expiry, and an account disabled since it signed in loses its session on its
  next request.
- A faculty switched off could never be switched back on: the list hid the
  disabled rows and the only path to active ran through creation, which refused
  the code as already used. The edit that disables it now re-enables it.
- A year typed into the holiday calendar or the balance adjustment reached
  Oracle's `NUMBER(4)` column before anybody looked at it, so `100000` was a
  database error instead of a refusal. Both screens bound the year now.
- The mail test endpoint sent before it logged, and the log column holds 320
  characters, so an over-long address could go out with no record that it had.
  The length is checked first.
- The account search was the only search on the platform with no cap, and its
  join (roles, employee, department) is the expensive one. It is paged at fifty,
  like the staff screen.
- A first `clone` could not run the documented commands: `backend/mvnw` had no
  execute bit, and the build had no production front-end profile, so the
  packaged JAR served a development page. `.env.example` claimed Spring Boot
  reads `.env` by itself, and its bootstrap block named a sample department
  that `V006` deletes — a fresh schema could not create its own first
  administrator. It names `ADMIN` now.

### Known gaps

Written down rather than fixed, with the reason in the document that owns it:

- The temporary password is still handed to the administrator in clear text,
  once. Mailing it needs a verified domain and a relay — see the accepted-risk
  list in [`docs/SECURITY.md`](docs/SECURITY.md).
- An unknown username and a wrong password take different times to refuse; the
  ceilings in front of them are what stop scripted guessing.
- The URL authorization rules have no automated test;
  [`docs/TEST-PLAN.md`](docs/TEST-PLAN.md) has the manual table.
- Status in the grids is plain text, not a coloured badge.
- `docs/` describes the architecture, the leave rules, the database, security,
  administration, operations and testing. Deployment notes live in
  [`INSTALL.md`](INSTALL.md) and [`docs/OPERATIONS.md`](docs/OPERATIONS.md).

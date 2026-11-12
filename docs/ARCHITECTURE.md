# Architecture

## Context

```
Browser (Vaadin UI, server-driven)
        |
        | HTTPS
        v
Reverse proxy (Nginx or Apache, university TLS certificate)
        |
        | HTTP on loopback
        v
Spring Boot application (Java 21, port 8080)
   |         |            |             |
   |         |            |             +--> University mail relay (SMTP)
   |         |            +--> File storage (local disk or NAS)
   |         +--> Background job queue (database-backed)
   +--> Oracle Database Free (schema LEAVEAPP)
```

The user interface is written in Java with Vaadin Flow, so the application
serves both the HTML/JavaScript shell and the JSON API from one origin. The
browser holds only a session cookie; changes are rendered on the server and
pushed to the browser, and no separate SPA client is deployed (ADR-014).

## Technology choices

| Layer | Choice | Reason |
| --- | --- | --- |
| Language | Java 21 | Required by Vaadin 25; LTS; Spring Boot 4 supports it |
| Framework | Spring Boot 4.1.2 | Current stable, standard, large hiring pool |
| Security | Spring Security 7, OAuth2 client | SSO-ready, mature session and role handling |
| Persistence | Spring Data JPA, Flyway migrations | Schema changes are versioned and reviewable |
| Database | Oracle Database Free (23ai or 26ai) | Matches the university's Oracle standard at no licence cost; see ADR-013 for limits |
| Front end | Vaadin Flow 25 with the Aura theme | Pure Java screens, no separate JavaScript build or client codebase to maintain (ADR-014) |
| Build | Maven wrapper | No global Maven install; reproducible builds |
| Tests | JUnit 5, Mockito | Unit coverage; no integration tier yet, see `docs/TEST-PLAN.md` |
| Monitoring | Spring Boot Actuator health probes | Simple, standard; extended in operations phase |

## Module layout

```
com.example.leaveplatform
  Application.java   Vaadin app shell (theme)
  config/            security, Jackson, OpenAPI, clock
  auth/              the session endpoints an API client reads (`/csrf`, `/me`,
                     its own `/password`) and `CaptchaService`, the security code
                     on the sign-in screen. The code lives in the visitor's
                     `HttpSession` as a keyed digest of the answer and never the
                     answer, which is also what
                     `config/CaptchaEnforcementFilter` reads a moment later when
                     the form posts to `/login` - the session is the only place
                     the two halves of one sign-in can meet. The filter runs
                     before Spring Security's own password check, so a request
                     with no code never reaches the database. The same service
                     counts the caller as well as the answer: pictures per
                     address, pictures per browser and wrong answers per browser,
                     each of which can end in a wait that outranks the password.
                     Task 49 added the optional authenticator second factor here.
                     `OneTimeCodes` does RFC 6238 TOTP in-house (base32 seed,
                     HMAC-SHA1, six digits, thirty seconds), written directly
                     because the platform builds offline from a local Maven
                     repository and the standard is that small. `EnrolmentCipher`
                     seals a seed for the database with AES-GCM under
                     `LEAVE_TWOSTEP_KEY`; it is a deliberate copy of the mail
                     `SecretCipher` construction rather than a shared class, so
                     the two secret kinds can be rotated and audited apart.
                     `TwoStepKey`, `TwoStepService`, `TwoStepController` and
                     `TwoStepRuleException` hold the enrolment, confirm and
                     turn-off flow and the `/two-step` endpoints; a seed being set
                     up is kept in this browser's `HttpSession` (`app.two-step.seed`,
                     `.expires-at`, `.wrong-codes`), never the database, until a
                     live code confirms it. `config/TwoStepEnforcementFilter`
                     reads the `otp` parameter at sign-in and is ordered between
                     `CaptchaEnforcementFilter` and `UsernamePasswordAuthenticationFilter`,
                     so the code is refused before the password is looked at (the
                     four reasons are ADR-021), and `config/TwoStepExceptionHandler`
                     turns a broken rule into a readable 400.
  limit/             `RequestThrottle`, the one place the platform counts how
                     often a caller asked for something. It is a fixed window per
                     key, held in memory with a ceiling on the keys it will
                     remember, and it answers either "serve this" or "how long is
                     left". Two doors hand it an address: the code picture and the
                     public printed-form check. It sits in its own package because
                     both `auth/` and `reporting/` need it and neither should
                     depend on the other for it.
  ui/                Vaadin views (login, start page, forced password change,
                     my leave, approvals, delegated approvals, administration,
                     leave configuration)
                     and the navigation guard that enforces a pending password
                     change. Shared chrome lives here too: `BrandHeader` (logo,
                     product name, role-filtered navigation, user, sign out),
                     `NotificationsBell` (the unread count over the icon in that
                     header, and the messages under it - see `notification/`),
                     `CreditFooter` (the developer credit shown on every
                     screen), and `AccountsSection` (create, reset, re-role,
                     disable sign-in accounts on the Administration screen).
                     `MailSection` sits under it on the same screen: the mail
                     server, sender address, kind list, a write-only password box
                     and the last attempts, so the answer to "why did nobody get
                     an e-mail" is on the screen rather than in a log file.
                     `Feedback`, `RemarkDialog`, `Grids` and `Rows` are the
                     shared pieces of interface: the toast, the confirm box (with
                     or without a remarks field, depending on whether the action
                     stores one), the height-and-empty-message rules every grid
                     follows so a long list scrolls inside its own box and an
                     empty one says why, and the form row that wraps on a narrow
                     screen. `FilterRow` is the period-and-status row above every
                     filtered list (blank part means "any", and choosing a first
                     day greys out the earlier days in the second box, so a period
                     the server would refuse cannot be clicked), and `ListNote` is
                     the one sentence above the list that says what it matched and
                     whether the server stopped counting. `RoleLabels` and
                     `ActionLabels` turn the codes the database holds
                     (`CENTRAL_ADMIN`, `LEAVE_APPROVED`) into the words a person
                     reads, and the audit search accepts either
                     spelling so a code typed from this documentation still works.
  organization/      faculty, department, office, hierarchy
  identity/          accounts, roles, sessions, SSO mapping
  leave/             types, balances, applications, approvals, withdrawal,
                     compensatory (no-deduction) approval, and the `LeaveFilter`
                     (period plus status) that narrows every list. A filter is
                     compiled to a JPA `Specification` in
                     `LeaveApplicationSpecifications` rather than a `@Query` with
                     optional parameters, because Oracle will not compare a date
                     column against a bind it has to type itself, and a clause
                     that is simply left out when blank cannot do that.
  admin/             administrator actions: faculties, departments and offices,
                     employee records with optional details, CSV import,
                     sign-in accounts, the public holiday calendar
                     (`HolidayAdminService`) and one employee's yearly balance
                     correction (`BalanceAdminService`)
  reporting/         the reads a department works from: `ReportService` builds
                     the leave register (one request per row, newest first) and
                     the staff summary (one employee per row, for one year), and
                     each is also written as a spreadsheet file by `CsvFile`.
                     `ReportController` exposes both as JSON and as a download.
                     A report never re-asks the database its own way: it reuses
                     `LeaveApplicationSpecifications`, so a file and the screen
                     it came from cannot disagree. `ReportsView` (`/reports`)
                     shows both to a central administrator and an HOD, with the
                     download of the same question under each. The same service
                     also prepares `IsoForm` rows - one per request, with the
                     office answers already decided - and `IsoFormPage` writes
                     them as the university's paper: A4 sideways, two
                     135-by-190-millimetre forms to a sheet, a dashed cutting
                     line between them, and a toolbar that never reaches the
                     printout. `VerificationCode` keys the request's own number
                     with `LEAVE_VERIFY_KEY` (ADR-019) and `QrGlyph` draws the
                     result as SVG, so `GET /api/v1/reports/verify` - the one
                     endpoint here that needs no sign-in - can answer whether a
                     sheet on a desk is genuine without telling the caller
                     anything about the person who asked for leave. `Paper`
                     holds the rules both printers share - a readable day and
                     the escaping that keeps somebody's name or reason out of
                     the markup - and `EmployeeRecordPage` uses them for one
                     officer's year: A4 upright, a sheet per name, the granted,
                     spent and left figures beside the requests that produced
                     them, every request whatever was decided about it, and two
                     signature lines.
  audit/             append-only audit trail: `AuditService` writes one row
                     inside the transaction that changes data, `AuditAction`
                     holds the action codes, and a read-only endpoint and
                     screen expose the newest entries inside a window. A blank
                     date is sent as the widest range the column can hold, never
                     as a null, for the same Oracle reason.
  notification/      the in-app bell, and nothing else yet. `Notification` is one
                     message addressed to one sign-in account (a foreign key, not
                     free text like `audit_event.actor_username`, because a message
                     has to have a reader who can open it); `NotificationService`
                     stores, lists and marks them read; `NotificationController`
                     shows a person their own inbox and no one else's, and
                     `NotificationKind` carries the screen each kind belongs to, so
                     a message can never point at a page the platform does not
                     have. Who is told what is deliberately **not** here:
                     `leave/LeaveNotifier` picks the recipients and words the
                     messages, because only the leave module knows whose queue a
                     request has just entered or left. Storing a message also
                     publishes `MessageStored`, which is how e-mail gets told
                     without the leave module knowing e-mail exists.
  mail/              optional e-mail copies of the bell messages. `MailSettings`
                     is the one row of the `mail_setting` table - server, port,
                     sign-in name, sender address, TLS, which kinds are mailed,
                     and whether any of it is switched on at all; the SMTP
                     password is kept in `password_cipher` sealed by `SecretCipher`
                     (AES-GCM under `LEAVE_MAIL_KEY`) and no read path gives it back,
                     only `hasPassword`. `MailSettingsService` validates and audits
                     a save, `MailService` decides whether a message leaves the
                     building and writes one row per attempt to `mail_log`
                     (`SENT` / `FAILED` / `SKIPPED` with the reason), `MailTransport`
                     is the only class that opens a mail socket, and
                     `ContactLookup` (in `identity/`) answers what address a
                     sign-in name may be mailed at. `MessageStoredMailer` listens
                     **after commit**, so a mail server that is down cannot roll
                     back an approval and a rolled-back request is never mailed
                     about. `MailController` (`/api/v1/admin/mail`) and
                     `ui/MailSection` are the screen; a failed send says so on the
                     screen instead of failing silently.
  support/           the help desk. `SupportTicket` is one open question from one
                     member of staff, with a `TicketStatus` that only ever says
                     whose turn it is (`OPEN` / `IN_PROGRESS` wait for the desk,
                     `ANSWERED` waits for the person who asked, `CLOSED` is
                     finished) and a `TicketCategory` saying what it is about.
                     `TicketMessage` is one line of the conversation and is
                     append-only: no update or delete path exists anywhere, so a
                     reply cannot be reworded after the fact, and `from_desk` says
                     which side wrote it. `TicketService` holds the two rules -
                     which side is speaking comes from the signed-in account and
                     never from the request body, and only the person who asked
                     and the desk may read a ticket - and audits the desk's
                     decisions but not a staff member's ordinary lines.
                     `TicketNotifier` picks the recipients, which are the enabled
                     `CENTRAL_ADMIN` accounts, and reuses the bell: it writes
                     `TICKET_*` notifications, so e-mail follows whatever the
                     administrator switched on without this module knowing e-mail
                     exists. `TicketController` (`/api/v1/tickets`) and
                     `AdminTicketController` (`/api/v1/admin/tickets`) are the JSON
                     side, `ui/TicketsView` (`/tickets`) and `ui/TicketsSection`
                     (a card on `/admin`) are the screens, and both screens build
                     the same `ui/TicketThreadPanel` with one boolean deciding the
                     buttons. `config/TicketExceptionHandler` turns a broken rule
                     into a readable 400.
  profile/           record corrections. `ProfileRequest` is one person's
                     proposal to fix their own staff record, decided as a whole
                     so a wrong name and a wrong phone get one answer, not two.
                     The rule of the module is `changed_fields`: the list of
                     fields the request names is the only statement of what will
                     be written, and `ProfileField` - one enum constant per
                     correctable column - owns the label, the column width, the
                     read and the write, so nothing outside the enum can widen
                     the set or apply a column nobody asked about (ADR-017).
                     `ProfileRequestService` holds the rules: a person proposes
                     only changes to their own active record, a value the record
                     already holds is refused with a sentence, one pending
                     request per person, refusing requires a note so the refusal
                     can be acted on, and approving re-reads the live record and
                     re-checks each proposed value before writing, which is why
                     a request overtaken by events is refused in words rather
                     than on a database constraint. `ProfileNotifier` reuses the
                     bell with `PROFILE_*` kinds, so e-mail follows the
                     administrator's existing switch. `ProfileController`
                     (`/api/v1/profile`) and `AdminProfileRequestController`
                     (`/api/v1/admin/profile-requests`) are the JSON side,
                     `ui/ProfileView` (`/profile`) and `ui/ProfileSection` (a
                     card on `/admin`) are the screens, and both build the same
                     `ui/ProfileChangesPanel` with one boolean deciding the
                     buttons - the panel reads the current value live each time
                     it is drawn, never from the request.
                     `config/ProfileExceptionHandler` turns a broken rule into a
                     readable 400.
  delegation/        handed-over approving. `ApprovalDelegation` is one dated
                     window in which a named colleague may act for a supervisor
                     or a head of department, and it is the whole answer to the
                     absence problem: nobody ever signs in as the absent person,
                     so the audit trail always names the hand that pressed the
                     button (ADR-018). The window carries a `DelegationScope` -
                     `SUPERVISOR` covers the recommending step of the staff who
                     report to the delegator, `DECISION` covers the department
                     the delegator heads, snapshotted into the row at the write
                     so a later transfer cannot move an old window's authority.
                     A window expires by date comparison (`covers(day)`), never
                     by a job; only closing one early is an action, and that
                     belongs to the delegator or to the office, not to the
                     holder. `DelegationService` holds the rules: the signed-in
                     staff record is always the delegator, the colleague must be
                     active with a working account, a window cannot start in the
                     past or reach further than `MAX_WINDOW_DAYS`, the scope must
                     be one the delegator actually holds, and one unhanded-back
                     window per person per step is checked under a pessimistic
                     lock on the delegator's staff row - `V016` explains why a
                     unique index could not be the guard. `DelegationNotifier`
                     reuses the bell with `DELEGATION_*` kinds, and the kind
                     `DELEGATION_ACTED` is how the delegator learns the window
                     was used: `leave/LeaveNotifier` tells them when a request
                     was handled under one. `DelegationController`
                     (`/api/v1/delegations`) is the JSON side, `ui/DelegationsView`
                     (`/delegations`) the screen, and
                     `AdminDelegationController` (`/api/v1/admin/delegations`)
                     the office's read-only register. `config/DelegationExceptionHandler`
                     turns a broken rule into a readable 400.
  shared/            errors, pagination, value objects
```

Static brand assets (`app.css`, `logo.png`) are served from
`backend/src/main/resources/META-INF/resources`; Vaadin bundles them into the
application, so there is no separate front-end build.

Each module owns its entities, repositories, services and web controllers. A
module may call another module's service, never its repository directly. This
keeps boundaries clear for future maintainers.

`reporting/` is the one exception, and it is an exception of reads only: a
report aggregates leave, employee and balance rows in one pass, which no single
owning service offers. It goes to those repositories for reads, never writes,
and reuses the owning module's filter (`LeaveFilter` and
`LeaveApplicationSpecifications`) rather than writing its own SQL, so the
aggregation cannot drift from the rules. See ADR-015.

## Multi-department model

- One database, one university, `department_id` on every department-owned
  record (leave applications, balances, attachments, audit events).
- A **department scope** is resolved from the authenticated user and passed
  explicitly to services. Repositories always filter by scope; cross-department
  queries exist only in central-admin and auditor services.
- Department codes are immutable once used; renaming changes the display name
  only. Merging marks the old department inactive and records the successor.
- Negative tests prove that a user from department A cannot read or change
  department B data, including by guessing identifiers.

## Reading lists

- Every list read is capped: `LeaveService.LIST_LIMIT` and
  `AuditService.LIST_LIMIT`, both 200, and the screen prints the sentence that
  says the newest 200 are shown and the dates should be narrowed. Without that
  line a person who asks for a whole year and gets two hundred rows reads it as
  "this is all of it".
- A narrowing question is answered in the database, not in Java. The filters
  become a `Specification` (leave) or the dated `@Query` window (audit), so the
  cap applies to rows that already match, and the newest rows are the ones kept.
- Sorting is part of the query, not of the screen: my requests come newest
  first, approval queues oldest first, because a waiting request should be the
  next one an approver reads.
- `LeaveApplicationRepository` extends `JpaSpecificationExecutor`, which bypasses
  any `@EntityGraph`, so the lazy `employee`, `employee.department` and
  `leaveType` proxies are pulled in batches by
  `hibernate.default_batch_fetch_size: 50` in `application.yml`. Dropping that
  line turns each list of 200 into hundreds of small queries against Oracle.

## Security architecture

- Default-deny security chain: health probes and the sign-in page are public;
  every other page and API call requires an authenticated session.
- Vaadin views carry an explicit access annotation; the navigation access
  control refuses a view without one.
- Session cookie (`HttpOnly`, `Secure`, `SameSite=Lax`) with server-side
  session; CSRF token required for state-changing requests.
- Accounts are local; SSO through OIDC is proposed and not built
  (`docs/DECISIONS.md`), and two-step signing is optional per account.
- Role checks at method level, plus department scope checks in services.
- Audit events for sign-in, lockout and every privileged change: accounts,
  roles, passwords, employees, departments and leave decisions. The entry is
  written in the same transaction as the change, so it cannot describe a change
  that did not happen.

Details and the threat model are in `SECURITY.md`.

## Reporting and background work

Built now:

- A screen read is capped at `ReportService.SCREEN_LIMIT` (200) rows, the same
  ceiling the leave lists use, and the file export is not: it reads
  `EXPORT_PAGE` (500) rows at a time until the data runs out, so a spreadsheet
  never quietly stops short of a department's year.
- Counting a year of days is done by the database (`totalsForYear` groups by
  employee and status); Java only assembles the rows a person reads.
- A spreadsheet is written with the byte-order mark and CRLF line ends Excel on
  a desktop machine expects, and a cell that begins with `=`, `+`, `-` or `@`
  is neutralised, because a reason someone typed would otherwise run as a
  formula on the reader's machine.
- Every download writes a `REPORT_EXPORTED` audit entry naming the report, the
  department or "the whole university", the filter it was taken with and how
  many rows it handed over. Who took a list of other people's leave is a
  question the trail answers.
- `SecurityConfig` limits `/api/v1/reports/**` to a central administrator and an
  HOD, and `ReportService` narrows again: an HOD is fixed to their own
  department, and asking for another is refused instead of quietly answered.
- The `/reports` screen asks the same service, and its download link points at
  the same endpoint with the choices currently on the screen. An HOD is never
  offered the department box: `ReportService.scopeFor` says whose rows may be
  read, and the screen only hides what the service would refuse anyway.
- The monthly attendance sheet marks only what the platform holds - the leave
  requests covering each day (`ReportService.buildSheet` reuses `registerQuery`,
  so a cell and the register cannot disagree), the holiday calendar for **H** and
  Saturday/Sunday for **W**. A day with nothing recorded is left blank rather
  than marked as presence, because nothing in this system records attendance;
  the legend that says so travels with the record. Weekend and holiday days are
  never charged, which is why an approved request can show fewer marks than its
  own day count.

Still to be built:

- Report generation in a database-backed queue processed by a worker, for
  volumes where a synchronous read is too slow (ADR-009).
- Generated files stored outside the web root with random names and a
  short-lived download ticket; today a file is streamed straight to the
  requester, so nothing is left on disk to expose.

## Configuration and environments

- All configuration comes from environment variables; no secrets in the
  repository. `.env.example` documents the names only.
- Three environments: development, staging and production. Staging mirrors
  production and is used for rehearsals and restore drills.
- Database credentials are scoped to `leaveapp` only.

## Deployment assumptions

- University IT provides a virtual machine with SSD storage, at least 4 vCPU and
  8 GB RAM, plus nightly off-site backup.
- The application runs as a service (systemd on Linux or a Windows service),
  behind the university reverse proxy with a valid TLS certificate.
- The current desktop and mechanical disk are development only.

## Evolution

- A later version keeps a modular monolith. Splitting reporting or notifications into
  separate services is justified only by measured load.
- The commercial-testing domain is added as new modules (`testing/`, `finance/`) on the same
  identity, organisation and audit foundations.

# Security

This document is the baseline for the new platform. It is reviewed at every
phase gate and updated when the threat model changes.

## Principles

1. **Default deny.** Nothing is public unless explicitly listed.
2. **Least privilege.** Database users, application roles and department scopes
   grant the minimum needed.
3. **Defence in depth.** Validation, authorisation and audit are independent
   layers; no single check is trusted alone.
4. **No secrets in code.** Credentials come from the environment or a secret
   store, never from the repository or a committed file.
5. **Personal data is a liability.** Collect the minimum, keep it only as long
   as policy requires, and restrict who can see it.

## Authentication

- Accounts are local to the platform: a username, a BCrypt hash, and the roles
  an administrator grants. Single sign-on is not built here; the OIDC
  integration in `docs/DECISIONS.md` stays proposed until whoever deploys this
  knows their identity provider.
- A second factor is available and is optional: any account can turn on an
  authenticator-app code (ADR-021), and it is offered only while
  `LEAVE_TWOSTEP_KEY` exists. It is not made compulsory for administrators,
  auditors or anyone else, because this version will not lock an account that
  was never enrolled. That rule is still to build.
- Password policy for local accounts: minimum 12 characters, checked against a
  breached-password list where available, last five hashes retained to block
  reuse, forced change on first login, lockout after repeated failures.
- Sessions: server-side session, `HttpOnly`, `Secure`, `SameSite=Lax` cookie,
  8-hour absolute lifetime, 30-minute idle timeout, rotated on login and on
  privilege change.
- Logout invalidates the server session and clears the cookie.
- Authentication events (success, failure, lockout, MFA) are logged with source
  address and user agent, never with the password.

### Implemented today

- The sign-in screen is a Vaadin view at `/login`; its form posts to Spring
  Security's `/login` filter. Sign-out uses `AuthenticationContext.logout()`,
  which posts to `/logout` with a CSRF token. `GET /api/v1/auth/me` returns the
  signed-in profile, `GET /api/v1/auth/csrf` returns the CSRF token and
  `POST /api/v1/auth/password` changes the caller's own password.
- Vaadin's security configurer owns form login, logout, CSRF and the matchers
  for Vaadin's own static resources and framework (UIDL/push) requests. The
  application adds its permit rules (health probes, CSRF token) **before**
  Vaadin's rules, then declares `anyRequest().authenticated()` through Vaadin's
  `anyRequest` hook: in Vaadin 25 that default is deny-all, so omitting this
  declaration locks out every page and API call.
- The brand assets the sign-in page needs before anyone is authenticated are
  permitted for `GET` only: `/app.css`, `/logo.png` and `/favicon.ico`.
  They are served from `META-INF/resources`, which the security chain treats
  like any other path, so without this rule the unauthenticated sign-in page
  loads unstyled and without the logo. Nothing else under that directory is
  public.
- The sign-in screen asks for a security code before it looks at a password.
  `auth/CaptchaService` draws a six-character picture (`GET /api/v1/auth/captcha`,
  permitted for anonymous `GET`, `Cache-Control: no-store`) and keeps a keyed
  digest of the answer - HMAC-SHA256 under a random key that lives only as long
  as the process - in that visitor's own HTTP session. There is therefore no
  server-side table of codes to grow or sweep, and a session that reaches a log,
  a debug endpoint or a heap dump hands out no live code. Restarting the
  platform retires the outstanding codes, which costs nobody a trip back to the
  screen. `config/CaptchaEnforcementFilter` runs **after** Spring
  Security's CSRF filter and **before** `UsernamePasswordAuthenticationFilter`,
  so an attempt with no code never reaches the database, never compares a hash
  and never produces the "wrong password" page that would confirm an account
  exists. The digest is compared with `MessageDigest.isEqual`, so how long a
  check takes tells a guesser nothing about how close it came. Every code is
  spent: showing a picture issues an answer, and a check throws that answer away
  whatever was typed, so one picture buys one attempt and a wrong password costs
  a fresh code. Codes expire after five minutes, are read case-insensitively,
  and avoid `I`, `L`, `O`, `0` and `1`. The answer
  never travels to the browser and never comes back from it: the screen only
  learns whether a typed code was right. The alphabet and the five-minute life
  match what staff already type elsewhere, so there is one kind of code. Any
  script that signs in - the checks under `docs/TEST-PLAN.md` and the local
  proxy used for browser passes - must now fetch
  `GET /api/v1/auth/captcha` with its own cookies, read the six characters out
  of the picture and send them as the `captcha` field of the `POST /login`, or
  it will be turned back before it reaches the password.
- Three counts stand behind that picture, each answering a different kind of
  caller, and all of them are in `application.yml` under `app.sign-in` so an
  office that needs a different balance asks for it instead of waiting for a
  release:
  - **per address** - `code-pictures-per-address-per-minute` (120) pictures a
    minute, held in `limit/RequestThrottle` rather than in a session because a
    stranger who has not been given a session yet is exactly who this one is
    about. It is the ceiling on how fast anyone can create sessions to knock on
    the door.
  - **per browser** - `code-pictures-per-session` (40) pictures, so one visitor
    cannot ask for codes forever.
  - **per wrong answer** - `wrong-codes-per-session` (3) guesses, counted for
    the attempts that had no code to answer at all, because a script that posts
    sign-ins it never saw a picture for is the behaviour the count ends.
  A spent count is a wait (`wait-after-too-many-seconds`, 120): during one the
  session is shown no new picture and its sign-in attempts are refused before
  the password is looked at, so the wait cannot be spent while the clock runs
  and a good password typed during it learns the caller nothing about the
  account. The picture box then holds a picture that says
  `Too many codes asked for. Try again in N seconds.` instead of a code, the
  endpoint answers `429` with `Retry-After`, and the screen says why - a grey
  box would only invite the clicking that the box exists to stop. A right code
  clears the guess tally, and a wait clears itself as it expires, so a person
  who waits it out starts level. The counts are per browser and per address, so
  one visitor's wait never stops the next one, and the numbers are set well
  above what a person at a desk does: nobody signs in forty times a minute.
- An account owner can turn on a second step, an optional authenticator-app code
  (RFC 6238 TOTP: six digits, HMAC-SHA1, a 30-second step, one step forgiven
  either side so up to a minute of clock skew, not configurable upwards because
  every extra step is how long a captured number stays useful). It is per
  account and off until the person enrols it, because making it a condition of
  signing in would push clerks and heads of department back to the paper
  register. `config/TwoStepEnforcementFilter` checks the code **after**
  `CaptchaEnforcementFilter` and **before** `UsernamePasswordAuthenticationFilter`,
  so every attempt that reaches it has already answered a picture (those counts
  are the ceiling on guessing a six-digit number), a refused code never reaches
  the password hash, and both a wrong code and a wrong password reach the same
  `/login?error` with no word about which half failed. The person types all
  three on the one screen; the box is labelled "Code from your app", "Only if you
  use two-step", and posts as the request parameter `otp` through the same
  hidden-field trick the `captcha` field uses. A refused code is counted into the
  **same** lock as a wrong password (`AppUser.registerFailedAttempt`, five per 15
  minutes), so an enrolled account is no softer a target than an unenrolled one.
  The seed lives on `app_user` as `two_factor_secret` (sealed `v1:` plus base64
  AES-GCM by `auth/EnrolmentCipher`) beside `two_factor_enrolled_at`, under two
  `CHECK` constraints (`V018`); whether the step happens is read from the presence
  of a seed, there is no "required" column. It is sealed under `LEAVE_TWOSTEP_KEY`,
  deliberately separate from `LEAVE_MAIL_KEY` so one leaked file is not both the
  mail account and every second factor, and enrolment is refused while that key
  is missing rather than storing a seed in the clear. Nothing is saved until the
  person confirms with a live code, which is what prevents a mistyped seed
  locking its owner out; three wrong confirmations throw the pending seed away.
  Turning it off needs the password **and** a live code (`POST /api/v1/auth/two-step/off`),
  because a stolen signed-in browser must not be able to remove the factor quietly,
  and the administration team can clear it for somebody else from the accounts list
  (`DELETE /api/v1/admin/accounts/{id}/two-step`) as the recovery for a lost phone
  or an unopenable seed, but never for their own account and never to enrol.
  Endpoints (`auth/TwoStepController`, `GET /api/v1/auth/two-step` and the
  enrolment, confirm and off calls) all require the account's own session, refuse
  in a 400 ProblemDetail titled "Two-step signing not accepted"
  (`config/TwoStepExceptionHandler`), and need no new `SecurityConfig` permit rule.
  The reasoning, and the four reasons for checking before the password, are ADR-021.
- One read is public on purpose: `GET /api/v1/reports/verify`, which confirms a
  printed leave form. It is permitted for anonymous `GET` and nothing else under
  `/api/v1/reports/**` is. What it may say is the form's own text - number, name,
  designation, department, leave type, dates, day count, compensatory - plus the
  status as it stands today; it never returns the reason, the remarks, the staff
  number or the names of the officers who recommended or decided. It answers only
  when the caller already holds a twelve-character code that this server's
  `LEAVE_VERIFY_KEY` minted for that exact number (ADR-019), so the picture on the
  paper is the credential and no session is involved. Four properties keep it
  from becoming a way into the database: every failure - unknown number, wrong
  code, badly shaped pair - is the same 404 with the same sentence; the code is
  compared with `MessageDigest.isEqual`, so timing does not reveal how close a
  guess came; with no key configured the endpoint refuses everything with 503
  rather than accepting forms it cannot check; and since task 48 the same
  `limit/RequestThrottle` that watches the picture allows `checks-per-address-per-minute`
  (60) checks a minute from one address, after which it answers `429` with
  `Retry-After` and a problem that names no person, no leave and no reason. Its
  defence before that ceiling is the code space itself - one guess in 6.9e14 -
  and the fact that a wrong guess yields nothing a caller could not have read
  off the paper in their hand.
- `GET /api/v1/reports/employee-record` sits behind the same rule as the rest of
  `/api/v1/reports/**`: a central administrator or an HOD with a session, and
  nobody else. It prints one named person's year - number, dates, days, status -
  and deliberately not the reason they typed, because a sheet signed and filed is
  circulated further than the screen where the request was made. Whose names may
  be printed is settled for the whole pile before a single request is read, so a
  refused run leaves no partial paper and no audit entry.
- Accounts are created, reset and disabled only through
  `AccountAdminService`, which is reachable from the Administration screen and
  from `/api/v1/admin/**` (both `CENTRAL_ADMIN` only). It refuses to disable
  the caller's own account or to remove the caller's own `CENTRAL_ADMIN` role,
  so the last administrator cannot lock the system. Every create, reset, role
  change and disable is appended to the audit trail together with the actor's
  username.
- Privileged and authentication actions are recorded in the append-only
  `audit_event` table by `AuditService`: account creation, password reset and
  change, role replacement, account/employee/department changes, leave
  submissions and decisions, sign-ins and lockouts, and turning an account's
  two-step signing on (`TWO_STEP_ENABLED`), off (`TWO_STEP_DISABLED`) and the
  office clearing it for somebody else (`TWO_STEP_CLEARED`). The entry carries
  the time, the actor, an action code, the target and a short summary; it never
  carries a password, a hash or a leave reason. The insert is part of the same transaction
  as the change, so a rolled-back operation leaves no false entry. A central
  administrator and an auditor can read the trail on `/audit` and through
  `GET /api/v1/audit/events`; no other role can, and no role can edit or delete
  an entry through the application.
- The in-app bell is scoped by the session, never by a parameter.
  `NotificationController` takes the recipient from `Authentication#getName()` on
  every call - `GET /api/v1/notifications`, `POST /api/v1/notifications/read` and
  `POST /api/v1/notifications/read-all` - and every statement in
  `NotificationRepository` carries that recipient, so an id belonging to somebody
  else's message matches nothing and is quietly not marked read. A message needs
  no role rule of its own: it falls through to `anyRequest().authenticated()` and
  answers 401 to a stranger. A message carries a staff name, a leave type, dates
  and the remarks an approver typed - never a password, a hash or a security code.
  Nothing is cached in the page: the count and the list are read from the database
  when the bell is opened and again after anything is marked read.
  Marking read can also carry the subject a screen is showing -
  `POST /api/v1/notifications/read` with a `kind` marks only rows of that kind,
  and a blank or unknown kind marks nothing at all. The help desk needs this: a
  "mark read" button on a list of ticket messages must not quietly read a
  person's leave messages as a side effect.
- E-mail is off until a central administrator turns it on, and the credentials
  for it are stored sealed. `mail_setting.password_cipher` holds AES-GCM over
  `LEAVE_MAIL_KEY`, which lives in the environment and never in the database, so a
  stolen dump is not a mail account. No endpoint, DTO, log line or audit entry
  returns the password or the sealed value: `GET /api/v1/admin/mail/settings`
  answers with `hasPassword`, and the only way to change it is to type it again.
  A value that cannot be authenticated - tampered with, or saved under a
  different key - is refused rather than tried, because sending a wrong password
  repeatedly can lock the account it belongs to on the university's own server.
  The settings endpoints fall under `hasRole("CENTRAL_ADMIN")`, so no other role
  can point the platform at a server of its choosing.
- Mail is sent after the notification row is committed
  (`@TransactionalEventListener(AFTER_COMMIT)` in `MessageStoredMailer`), so no
  mail outcome can roll back a leave decision, and a request that was rolled back
  is never mailed about. Every reason not to send - switched off, kind not
  listed, no address on the staff record, closed account - is written to
  `mail_log` as `SKIPPED` with the reason, which is what lets a person prove the
  feature is working rather than guess that it is broken.
- A help-desk ticket is readable by two people only: whoever opened it, and the
  desk. `TicketService.thread` takes the caller from the session and compares
  their own staff record with `opened_by_employee_id`, and a colleague who guesses
  the number gets 403 rather than the conversation - which matters because a
  ticket routinely quotes a personal record, an account problem or a salary-
  related question. The desk's endpoints sit under `hasRole("CENTRAL_ADMIN")` and
  `deskAccount` checks the role again, because the Vaadin screen calls the service
  directly and one rule has to hold for both paths.
- Which side of a ticket is speaking is taken from the account and never from the
  request. `from_desk` on a line, and whether a caller may see a queue at all,
  both come from the roles on the session; there is no field in any request body
  that can claim to be the desk. Handing a ticket over is the same: `assign`
  accepts a sign-in name only if that account is enabled and holds
  `CENTRAL_ADMIN`, so a ticket can never be "assigned" to a name nobody can act
  on.
- A ticket's number is written by the database (`ticket_reference_bi`) and the
  column is read-only in the mapping, so no request, screen or service can put a
  number of its choosing on a row. Reference-guessing therefore yields a gap
  rather than a readable id.
- The thread is append-only: no update or delete path exists in the application,
  so what the desk and the staff member said to each other stays as it was said,
  with its author and its time. The desk's decisions - answering, handing over,
  closing - are audited; a staff member's own lines are not, because the thread
  already keeps every word and an approval-shaped entry for ordinary business
  would only make the audit trail harder to read.
- A record correction is owned by the person who asked. `ProfileRequestService`
  takes the caller from the session and the subject of a proposal is always the
  caller's own active staff record - there is no employee id in any request
  body - and reading or withdrawing a request compares its author, so a
  colleague who guesses a `PR-` number gets 403 rather than somebody else's
  name, number and phone. The desk's endpoints sit under
  `hasRole("CENTRAL_ADMIN")` and the service checks the role again, because
  the Vaadin screen calls the service directly and one rule has to hold for
  both paths.
- What an approval may write is decided by `changed_fields`, and `ProfileField`
  is the only door to an employee column: five constants, each owning its
  label, width and write. There is no path from a request to the employee row
  that is not through that list, which is the structural answer to the earlier
  failure where approving one correction nulled the columns nobody mentioned.
  Approving re-reads the live record and re-checks each proposed value before
  writing, so a request overtaken by events is refused rather than applied
  against a stale snapshot - the row deliberately stores no snapshot to apply.
- A correction's number is written by the database (`profile_reference_bi`) and
  the column is read-only in the mapping, like a ticket's. Refusing a request
  requires a note in words, because the person is sent exactly those words -
  the note is a channel into a staff inbox and is kept with the decision and
  the audit entry.
- A hand-over of approving cannot be pointed at somebody else.
  `DelegationService` takes the delegator from the signed-in staff record -
  there is no "from" in any request body - so a caller can only ever hand out
  their own approving, and the step must be one they actually hold: the
  recommending needs active direct reports, the final decision needs the
  `HOD` role, and the department a `DECISION` window covers is read from the
  delegator's own record and fixed into the row at the write. A central
  administrator's university-wide authority is not on the list at all, because
  that authority is the fallback for every screen, including this one.
- A window reaches only a colleague who can act on it: the delegatee is
  re-read as an active staff record with an enabled sign-in account, the
  window cannot start in the past, and every use of it asks whether the dates
  cover today - so an ended window stops granting access at midnight with no
  job, no flag and nothing to remember. Closing one early belongs to the
  delegator or to `CENTRAL_ADMIN`; the holder is refused, because an authority
  given is not the holder's to hand back.
- The delegatee never borrows an identity. The session, the account and the
  password of the absent person are untouched; what changes is whose queue the
  request appears in, and the audit detail carries "acting for &lt;name&gt;,
  DL-xxxxxx" beside the decision so the trail names the hand that acted and
  the authority it stood on. This is the structural answer to the old
  practice of signing in as the absent officer, which made the trail describe
  the credential used instead of the decision taken.
- A delegation's number is written by the database (`delegation_reference_bi`)
  and the column is read-only in the mapping, like a ticket's and a
  correction's.
- Vaadin navigation access control requires an explicit annotation on every
  view: `@AnonymousAllowed` on the login view, `@PermitAll` on the signed-in
  start page. A new view without an annotation is refused, which is the safest
  failure mode.
- CSRF tokens are required on every state-changing request. For the API the
  session cookie is `HttpOnly`, `SameSite=Lax`, and `Secure` when
  `LEAVE_COOKIE_SECURE=true`; the browser reads the `XSRF-TOKEN` cookie and sends
  it in the `X-XSRF-TOKEN` header. Vaadin renders and validates the token for
  the UI form.
- Unauthenticated `/api/**` calls answer 401 with no body instead of a 302 to
  the HTML login page, so scripts and probes are never redirected.
- Login failures return to `/login?error`, which shows one message for unknown
  user, wrong password, disabled account and locked account, so the response
  reveals nothing about the account.
- Passwords are stored as BCrypt hashes; the bootstrap administrator must
  change its password at first sign-in.
- An account that still has a temporary password is sent to the Vaadin
  change-password screen from every other route (`PasswordChangeGuard`), so the
  flag is enforced rather than merely recorded. The flag is read from the
  database on each navigation, and the screen uses the same `PasswordService`
  as the API, so one policy covers both entry points.
- `locked_until` is enforced when loading the account.
- Failure counting: five wrong passwords lock the account for 15 minutes.
  Failures on a disabled or already locked account are not counted, so a lock
  cannot be extended indefinitely, and a successful sign-in clears the counter.
  The response is the same error whatever the reason.
- `POST /api/v1/auth/password` changes the caller's own password. It requires
  the current password and enforces the policy: at least 12 characters, at most
  72 bytes (the BCrypt limit), not a common password, not containing the
  username, different from the current password and from the last five.
- Sessions end eight hours after sign-in even if the user stays active
  (`SessionAbsoluteExpiryFilter`), in addition to the 30-minute idle timeout.
  The filter stamps the sign-in time itself on the first authenticated request,
  so it does not depend on a login-success handler.
- Still to build: invalidating the other sessions of a user when the password
  changes, a breached-password service (today the deny-list is embedded),
  password expiry for privileged roles, and the role rule that would make the
  second factor compulsory. Each is a deployment requirement for an institution
  that wants it, and each is listed here rather than quietly missing.

## Authorisation

- Roles: employee, supervisor, HOD, PA/clerk, department administrator, central
  administrator, auditor (see `REQUIREMENTS.md`).
- Every department-owned record carries `department_id`; services require both
  the role and the department scope.
- Cross-department access is limited to central administrators and auditors and
  is audited.
- Identifier enumeration is prevented by scoping queries, not by hiding IDs.
- Negative tests are mandatory for every new endpoint: a user from another
  department, a lower role and an anonymous caller must each be rejected.

## Data protection

- Personal data inventory: name, employee number, department, designation,
  contact details, leave records, attachments. No national identifiers or
  salary data unless HR explicitly requires them.
- Encryption in transit: TLS 1.2 or newer, HSTS on the public host, no mixed
  content.
- Encryption at rest: Oracle Free does not include Transparent Data Encryption
  (an Enterprise option). Encryption therefore comes from the operating system
  (BitLocker or LUKS on the database volume) and from encrypted `expdp` backups.
  This must be confirmed with IT before the pilot; if whole-disk encryption is
  not available, record it as an accepted risk in `RISKS.md`.
- Backups are encrypted, stored off the application server, and restore-tested
  quarterly.
- Logs exclude passwords, tokens, attachments and unnecessary personal data.
- Retention: active records for the employment period, then per university
  retention policy; rejected applications and audit logs have explicit periods.

## Application hardening

- Input validation on every request (Bean Validation), parameterised queries
  through JPA, output encoded by the template/JSON layer.
- Security headers: `Content-Security-Policy` with `default-src 'self'`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
  `frame-ancestors 'none'`, HSTS in production.
- CSRF tokens for cookie-authenticated state changes; no CORS wildcard.
- File uploads: allow-list of types, size limit, random stored names, storage
  outside the web root, no execution permission, optional antivirus scan.
- Rate limiting on login, password reset, captcha and report generation. The
  platform does this for the two doors it opens to strangers - the code picture
  (`app.sign-in.code-pictures-per-address-per-minute`) and the printed-form
  check (`app.verify.checks-per-address-per-minute`) - through
  `limit/RequestThrottle`, and for the password itself through the five failed
  attempts that lock an account for fifteen minutes in `identity/LoginAttempts`
  (wrong authenticator codes since task 49 count into that same lock, so they
  cannot be used to walk a six-digit number either).
  A reset by e-mail is not built yet, and a report is a signed-in, audited read
  already bounded by the screen and print limits in `ReportService`, so neither
  has a ceiling of its own.
- Error responses never include stack traces or SQL.
- Dependency and container scanning in CI; CodeQL or equivalent on pull
  requests.
- Patch policy: critical within 7 days, high within 30 days, others in the next
  maintenance window.

## Secrets and accounts

- Database passwords, mail credentials, SSO client secrets and signing keys
  live in the deployment environment or a secret store, with access limited to
  the service account and named administrators.
- Separate credentials per environment; no sharing with the old applications.
- Rotation at least yearly and immediately after any suspected exposure.
- The `SYS`, `SYSTEM` and other DBA accounts are never used by the application;
  it connects only as the `LEAVEAPP` schema owner, which owns no other schema.

## Audit and monitoring

- Append-only audit table: time, actor, action code, target and a short
  summary. The application only inserts and reads rows and has no code path that
  updates or deletes one; granting the schema owner separate insert-only
  privileges, storing the source address and alerting are still to be added
  (**planned**).
- Alerts: repeated login failures, access-denied spikes, configuration changes,
  backup failures, certificate expiry, disk usage above 85%.
- Log retention aligned with the audit policy, at least one year.

## Incident response

1. Contain: disable the account or stop the affected component.
2. Preserve evidence: copy logs and audit rows before changes.
3. Notify: the named maintainer, IT security and the data owner.
4. Eradicate and recover: patch, restore from a verified backup if needed.
5. Review: written post-incident report with actions and dates.

## Privacy and legal

- The platform processes employee data under the university's employment and
  data-protection policies, not consumer consent.
- Access requests and corrections go through HR.
- A data-protection impact assessment is completed before the pilot.
- Data is not transferred outside the university without written approval.

## Accepted risks

Stated plainly, so this document is not read as a list of everything that was
closed.

- **A temporary password is handed back once, in clear text, to the
  administrator who asked for it** (`admin/IssuedCredentials.java`). Sending it
  by mail instead needs a verified institutional domain and a working relay,
  which is a pilot precondition, not a code change. What an unclaimed temporary
  password can do is narrow: it only ever changes itself, on the screen and on
  every API path.
- **A name that does not exist and a wrong password take different amounts of
  time to refuse**, because the unknown name never reaches the hash comparison.
  The scripted guessing that this gap invites is answered further up the chain -
  a security code the server drew, plus per-address and per-session ceilings - so
  no dummy hash run was added.
- **The URL authorization rules in `config/SecurityConfig.java` have no
  automated test.** The suite covers the services and the filters; the rules
  themselves are verified against a running server, and `docs/TEST-PLAN.md` says
  which checks are manual.
- **A two-step code stays acceptable for one 30-second step either side of now,
  and the platform keeps no record of codes it has already taken.** So a number
  somebody else happened to see is usable for up to about a minute. Marking a
  code spent is what would fix it — and a code is currently read before the
  password is checked, so spending it there would let anyone who knows a
  colleague's username lock them out of their own account by guessing a number.
  A shorter window, or spending the code only after the password passes, are
  both open to a pull request.
- **The mail, verification and two-step secrets have no rotation.** They live in
  the environment, never in the database, and changing one invalidates what it
  protects: losing the two-step secret means every enrolled person sets their
  authenticator up again from scratch.

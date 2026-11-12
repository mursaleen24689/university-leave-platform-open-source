# Test plan

What the automated suite proves, what it cannot prove, and how the rest is
checked. Read this before you rely on a green build.

## The suite

```bash
cd backend && ./mvnw verify
```

611 tests in 57 classes, plain JUnit 5 with Mockito for the repositories and
AssertJ for the assertions. No test starts a Spring context, opens a database,
or drives a browser — so `verify` passes on a laptop with no Oracle installed,
and it is what the continuous-integration workflow runs.

The servlet filters are exercised directly with Spring's
`MockHttpServletRequest`, `MockHttpServletResponse` and `MockFilterChain`.
Anything that needs a real database — migrations, Oracle-specific SQL,
transaction and locking behaviour — is outside the suite by design.

## What each area proves

| Area | Classes | What is pinned down |
| --- | --- | --- |
| Leave rules | `LeaveService`, `LeaveDayCounter`, `LeaveFilter`, `LeaveNotifier` | quota maths, the leave-year boundary, compensatory leave, withdrawal, approval chains, filter combinations |
| Balances and locking | `admin.BalanceAdminService`, `leave.LeaveService` | a balance is created once, and the order of row locks that stops two approvals spending the same day |
| Sign-in | `auth.CaptchaService`, `auth.OneTimeCodes`, `auth.TwoStepService`, `auth.EnrolmentCipher` | the code picture, RFC 6238 test vectors for the six-digit number, the sealed enrolment seed |
| Session filters | `config.*Filter`, `config.ForwardedHeaderPolicy` | who gets redirected, who gets 401/403, when a session dies, and which `X-Forwarded-For` is believed |
| Passwords | `identity.PasswordPolicy`, `identity.PasswordService`, `identity.AppUser`, `identity.LoginAttempts`, `identity.AppUserDetailsService` | history, forced change, lockout that expires by itself |
| Administration | `admin.*` | accounts, employees, organization, holidays and CSV import/export behaviour |
| Approvals and delegation | `delegation.*` | the dated hand-over of approving authority and its notifications |
| Reports and paper | `reporting.*` | CSV files, the printed ISO form and leave record, the signed verification code, QR glyph placement |
| Help desk, notifications, mail | `support.*`, `notification.*`, `mail.*` | ticket lifecycle, the bell's list, settings storage with the SMTP password sealed |
| Screen helpers | `ui.*` | the label and validation helpers a screen uses; see "What the suite cannot reach" |

## What the suite cannot reach

Stated plainly, because a green `verify` is easy to over-read.

- **The URL authorization rules in `config/SecurityConfig.java` have no
  automated test.** They are a chain of `requestMatchers(...)` calls owned by
  Vaadin's security configuration, and no unit test can assert that a request to
  `/admin` is turned away. A change there needs the manual pass below.
- **Vaadin screens cannot be constructed in a plain unit test.** A `RouterLink`
  needs a live router, so `new BrandHeader(...)` throws outside a running
  application. The screen tests therefore cover the static helpers the screens
  call (`MyLeaveView.leftBehind`, `MainView.plainRoles`, `AccountsSection.suggest`,
  `ActionLabels.plain`, `ListNote`, `RoleLabels`) and not the wiring of the
  components themselves.
- **Real Oracle behaviour.** Migrations, sequences, triggers that pad reference
  numbers, `SELECT ... FOR UPDATE`, and the licence ceilings are only proven
  against a real instance.
- **Anything visual.** Print layout, page breaks, and behaviour at phone width
  are settled by looking at them.
- **Sending mail.** `MailService` is tested as far as building and storing the
  settings; delivery needs a relay.

## The manual pass

Do this against a running server on a development schema — never a live one —
before calling a release done.

### Authorization

Sign in as each role and try the paths. Expected answers, both in the browser
and with a cookie-carrying script:

| Account | Try | Expect |
| --- | --- | --- |
| signed out | `/admin`, `/api/v1/leave`, `/api/v1/admin/accounts` | redirect to `/login`, and `401` for the API |
| signed out | `/api/v1/auth/csrf`, `/api/v1/auth/captcha`, `/api/v1/reports/verify`, `/actuator/health` | `200` — these four are open on purpose |
| staff, no approving role | `/approvals`, `/api/v1/reports` | turned away |
| head of department | another department's `/api/v1/leave/{id}/approve` | refused, with the reason |
| administrator on its issued temporary password | any `/api/v1/**` except `/api/v1/auth/**` | `403` until the password is changed |
| disabled mid-session | any screen | the next request ends the session |
| read-only auditor | a change on `/admin` | no control offered, and the API refuses |

### Sign-in and sessions

- Wrong password five times → the account is locked, and it unlocks by itself.
- A code typed after its picture expired → refused; a correct one → in.
- A session left open past the absolute limit → the next request goes back to
  `/login`.
- Two-step enrolled → the password alone is refused; a code from a phone with a
  drifted clock still works inside the window, and a code the platform already
  took stays usable until that step passes — it keeps no record of codes it has
  seen, which `docs/SECURITY.md` lists as an accepted risk.
- Behind a proxy: fire 130 captcha requests from one address in a minute and the
  ceiling must bite; the same from a rotating `X-Forwarded-For` must not fill the
  count from a non-internal proxy.

### Screens and paper

- Apply for leave across a year end, then change the first date so the last date
  falls behind it: the second picker clears itself and the helper text explains
  why.
- Approve, reject, withdraw, and compensate a request; check the balance before
  and after each.
- Print the ISO form and the leave record from a browser at A4, scan the QR with
  a phone, and check the number at `/api/v1/reports/verify`.
- Open the register, summary and attendance sheet with an empty department and
  with a page of rows; export each to CSV and open the file in a spreadsheet.
- Resize to phone width and check that the action cards wrap instead of clipping.

### Database

- Run every migration in order on a fresh schema, then restart the application
  and confirm Flyway applies nothing.
- Back up, drop the schema, restore, and sign in.
- Two approvals for the same employee and same balance at the same instant: one
  must succeed, the other must fail cleanly, and the balance must move once.

## What this project has never automated

No browser automation, no container-based database test, no load test, no
OpenAPI contract test. Each was a choice with a reason — see
[`DECISIONS.md`](DECISIONS.md) — and any of them would be a welcome pull
request, especially a Spring context test that asserts the authorization table
above instead of needing a human to click through it.

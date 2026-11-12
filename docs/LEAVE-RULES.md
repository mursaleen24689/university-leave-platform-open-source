# Leave rules

The rules below were taken from the single-department leave application this platform replaces
on 2026-10-30 so that staff meet the same process in the new platform. Where the
new platform deliberately differs, the difference is stated.

## Leave type and allowance

- One type in the first version: **Casual Leave**, code `CL`.
- 25 days per employee per calendar year (`leave_type.annual_allowance`).
- A balance row is created the first time an employee needs it and allocated the
  type's allowance for that year. The earlier application created the row when the account was made;
  the new platform creates it on demand, which needs no back-fill for imports.
  A central administrator can also create or correct a row for one employee and
  one year from **Leave setup**, and a reason for the change is mandatory there.

## Counting days

- The range is inclusive of both dates.
- A day is counted unless it is a public holiday that falls on Monday to Friday.
  Weekends are counted as leave days, exactly as before. The holidays
  themselves are the `public_holiday` rows maintained on **Leave setup**; there
  is no hard-coded list, and a university-wide calendar applies to every
  department.
- The server computes the day count; the browser never sends it. (The earlier application accepted a
  number from the browser and compares it with its own count.)
- Holiday-only ranges (count 0) are refused with a clear message; a day off that
  was never a leave day is not a request. Duty on such a day is covered by
  approving a real request as compensatory, which grants the time without
  deducting it.
- A request that overlaps an existing pending or approved request is refused.

## Approval

| Who | When |
| --- | --- |
| The employee | Submits for themselves only. |
| The employee's assigned supervisor (`employee.supervisor_employee_id`) | Recommends or rejects first, when one is assigned. |
| An HOD of the employee's department | Gives the final decision. |
| Central administrator | May give the final decision for any department. |
| A delegatee - the colleague a window names | Recommends or decides, as themself, for the days a `DELEGATION` window covers: the recommending of the delegator's own staff, or the decisions of the department fixed into a final-decision window. See `delegation/` and ADR-018. |

- An HOD's own request skips the supervisor step, as the earlier application did.
- Statuses: `PENDING_SUPERVISOR` → `PENDING` → `APPROVED` or `REJECTED`. The
  applicant can pull a request out of either waiting state, which sets
  `WITHDRAWN`.
- Only the assigned supervisor can act on `PENDING_SUPERVISOR`; only an HOD of
  the applicant's department (or a central administrator) can act on `PENDING`.
- A dated window is the one exception to those two names, and the queues and
  the buttons ask the same question of the same service, so a row a person can
  see is a row they can act on: a request whose supervisor handed their
  recommending over appears in the colleague's queue while the window covers
  today, and the same is true of a department's decisions under a
  final-decision window. Outside those hours the window grants nothing -
  expiry is a comparison of dates, not a flag somebody has to clear - and an
  action taken under a window is recorded as the colleague's own, with
  "acting for &lt;name&gt;, DL-xxxxxx" beside it and a notice to the delegator.
- A decision is refused if the status changed in the meantime, so two people
  cannot decide the same request.
- Approval deducts the days from the year's balance in the same transaction and
  is refused when the balance is short, as it always was.
- **Compensatory approval** grants the leave and deducts nothing. This is what
  The earlier schema recorded as `isCompensated` on the same decision screen, and it is what a
  department head uses when the days were earned back by duty on a holiday or
  weekend. The request is marked `compensated`, so a report can tell the two
  kinds of approval apart, and the audit entry says the balance was not touched.
- **Withdrawal** is the applicant's own action, allowed only while the request
  is still waiting. The earlier system deleted the row; here it is kept with status
  `WITHDRAWN` because the audit trail and the leave register have to show that
  the request existed. Nothing is deducted, since days are only spent on
  approval, and a withdrawn request no longer blocks an overlapping new request.

## Balance

- Stored as `allocated` and `used`; remaining is always `allocated - used` and is
  never stored twice (an earlier design stored `remaining`, which could drift from `used`).
- The year is taken from the start date. A request that spans 31 December is
  counted wholly against the start year, as the earlier application did.
- Only approval changes a balance, and a compensatory approval does not change
  it either. A rejected or withdrawn request changes nothing.

## Screens and endpoints

| Screen | Route | Shows |
| --- | --- | --- |
| My leave | `/leave` | The year's balance, a request form, the employee's own requests and a withdraw action on the ones still waiting; a period and a status narrow the list |
| Approvals | `/approvals` | Requests waiting for the signed-in supervisor, and requests waiting for the employee's HOD or a central administrator, with approve, approve-as-compensatory and reject actions; a period narrows both queues |
| Leave setup | `/leave-config` | The public holiday calendar for a year, and one employee's balance rows with a correction dialog that requires a reason (central administrator only) |

The same actions are available as JSON: `GET /api/v1/leave/balance`,
`GET /api/v1/leave/applications`, `POST /api/v1/leave/applications`,
`GET /api/v1/leave/approvals`,
`POST /api/v1/leave/applications/{id}/supervisor`,
`POST /api/v1/leave/applications/{id}/decision` (body field `compensate` for the
no-deduction approval) and
`POST /api/v1/leave/applications/{id}/withdraw`. On the administration side:
`GET /api/v1/admin/holidays?year=`, `POST /api/v1/admin/holidays`,
`DELETE /api/v1/admin/holidays/{id}`,
`GET /api/v1/admin/employees/{id}/balances` and
`PUT /api/v1/admin/employees/{id}/balances`. The browser sends dates only;
the day count in the answer is the server's own.

### Narrowing a list

- `GET /api/v1/leave/applications?from=&to=&status=` and
  `GET /api/v1/leave/approvals?from=&to=`. Every part is optional and a blank one
  means "any"; `from` or `to` alone is a half-open period, and a period whose
  last day falls before its first day is refused with 400 rather than answered
  with an empty list that looks like "nothing happened".
- A request belongs to a period when it **overlaps** it, so leave from 30 June to
  3 July still appears in a July search. Matching only the first day would hide
  days a department is actually missing without anyone on it knowing.
- Both approval queues only ever hold waiting requests, so that screen offers
  dates and not a status: a status there would either empty a queue or change
  nothing.
- Each list returns the newest (own requests) or the oldest-waiting (queues)
  `LeaveService.LIST_LIMIT` rows, and the screen says when the list was cut off.

### What the paper says

- Every request gets a number of its own, `LV-000123`, written by the database on
  the way in (ADR-016, `V017`). It is not the row id, it is never typed by anybody,
  and it appears on **My leave**, on both **Approvals** queues, as the first figure
  in the register and its file, on the printed form and on the printed leave
  record - so a sheet on a desk and a screen can be proved to be the same
  request.
- A form prints two to a sheet of A4 sideways, cut apart, and a print run is the
  rows ticked - at most 50 - not a whole year. An undecided request prints
  *may be / may not be* whole for the officer to tick; a decided one has the half
  the platform knows to be wrong struck through, because paper should not pretend
  the answer is still open. A department head's own leave asks the Dean (CPE), not
  their own chairman.
- The reason is printed only as far as the box holds - the first
  `IsoFormPage.REASON_ON_PAPER` characters, stopping at a whole word - and then
  says the rest is in the register under that number. The data keeps every word;
  only the sheet is shorter.
- A leave record is the other paper: one A4 upright sheet per person ticked on
  the staff summary, for the year on the screen, at most 50 names to a run. It
  lists every request that touches that year - approved, waiting, refused,
  withdrawn - with the days of a period counted across the days it spans, which
  is how the register and the day counter count them, so the paper's totals and
  the summary's figures cannot tell two stories. Nothing that was written as a
  reason appears on it, and its granted / spent / left line is stamped with the
  day the page was printed rather than presented as the state of 31 December: the
  platform can only speak for today.

## Not built in this slice

- Comp-off for duty on a public holiday that is never a leave request. The earlier application let
  a zero-day request be marked compensated; here a request that contains only
  holidays is refused, so compensatory approval only covers real requests.
- Half days; whole days only, as before.
- Other leave types (medical, annual, maternity, ...) and their caps.
- Attachments, email notices, and the yearly reset job (the allowance is per
  year, so a new year starts from the type's allowance automatically).

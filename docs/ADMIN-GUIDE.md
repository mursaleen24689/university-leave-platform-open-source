# Administrator guide

This guide describes the administrator functions. The screens described under
Organisation and People are on the **Administration** screen (`/admin`), which
only an account holding `CENTRAL_ADMIN` can see. Items marked **planned** are
delivered in a later phase; the data model already supports them.

Everything is written for a phone as well as an office monitor: the navigation
centres itself, a form puts one field on each line, and a button is never pushed
off the screen. A wide table (the employee list, the audit trail) keeps its own
horizontal scroll on a small screen, so a phone shows the first few columns and
slides sideways for the rest.

## Organisation

The public university website lists academic departments, but dean offices,
Registrar, Finance, Procurement, Examinations, Medical and Transport have no
staff pages. Those units are created here by the administrator, as data, without
a code change or a developer.

### Add a faculty or office group

1. Open **Administration**.
2. Under **Faculties**, enter a code (uppercase letters, digits and hyphens,
   2-16 characters) and the display name.
3. Choose **Add faculty**. It appears in the table immediately.

### Add a department or office

1. Open **Administration**.
2. Under **Departments and offices**, choose the faculty or office group, enter
   the code, the display name and the type (**Academic**, **Administrative** or
   **Support** - the screen spells them out; the API takes `ACADEMIC`,
   `ADMINISTRATIVE` or `SUPPORT`). Registrar, Finance, Procurement, Examinations,
   Medical and
   Transport are `ADMINISTRATIVE` or `SUPPORT` and sit under the `AO` /
   Administrative Offices group.
3. Choose **Add department**.

The code is permanent. If the department is renamed later, the code stays the
same so reports and imports remain consistent.

### Rename a department

Change the display name only (available through the API today; the button is
**planned**). Historical records keep the name valid at the time and show the
current name as well.

### Merge a department

**Planned.** Choose the department to retire and the department that will
continue; the retired department becomes inactive and records the successor.
Staff are reassigned by HR or the administrator; history stays attached to the
department where the leave happened.

### Deactivate a department

Use **Deactivate** in the department table. Deactivation hides the department
from new employee records and new applications, but keeps all history readable.
Choose **Activate** to bring it back.

Never delete a department that has records. Deletion is not offered.

## People and roles

### Add an employee

1. Open **Administration** and scroll to **Employees**.
2. Fill in the full name, the designation and the department. Choose
   **Save employee**.

Only those three fields are required. The staff number, e-mail, phone and
supervisor may be unknown when a person is first entered, because the public
website does not publish them for every office. Leave them blank and complete
them later with **Edit**; the record is valid in the meantime and can already
receive leave requests.

- The staff number, once entered, is the unique key; never reuse it.
- E-mail addresses are checked for shape and uniqueness.
- The supervisor is given as a staff number. An employee cannot be their own
  supervisor.
- Use **Deactivate** on departure the same day; history remains. **Activate**
  reverses a mistake.

### Find an employee

Type part of a name or a staff number, or choose a department. The list shows at
most the first 50 matches and says so underneath, because the university is
3,000 people and a search that returned everyone would be unreadable; narrow the
search rather than scrolling. The **Status** column reads Active or Inactive
rather than a yes-or-no mark, and an empty list states that nothing matched
instead of looking broken.

### Complete records in bulk

Paste rows from a spreadsheet under **Bulk import**. The first line may be the
header; it is recognised and skipped.

```
employee_no,full_name,designation,department_code,email,phone,supervisor_no
7021,Ayesha Khan,Assistant Professor,CS,ayesha@example.edu,+92 300 0000000,
```

- An existing staff number is updated, a new one is created.
- The department is given by its code and must be active.
- Rows without a staff number, with an unknown department or with a malformed
  e-mail are reported and skipped; the other rows are still applied.
- Quoted fields may contain commas; use two double quotes for a quote inside a
  quoted field.

## Sign-in accounts

An employee record and a sign-in account are separate on purpose: HR can enter a
person before their papers arrive, and only an administrator gives that person a
way to sign in. Accounts are managed under **Sign-in accounts** on the
**Administration** screen.

### Create an account

1. Open **Administration** and scroll to **Sign-in accounts**.
2. Choose the employee. Employees who already have an account are not offered,
   and employees marked inactive are not offered either.
3. Accept the suggested username or type your own. The username is permanent and
   must be 3-60 characters of lower-case letters, digits, dots, dashes or
   underscores, starting with a letter or digit. It cannot be changed later; if
   it must change, create a new account and disable the old one.
4. Leave the temporary password blank to let the system generate a strong one, or
   type your own. Either way it is shown **once**, in an "Account ready" dialog,
   with a copy button.
5. Optionally tick extra roles. Every account can request its own leave, so
   nothing has to be ticked for that; the list only offers the roles that add
   more than that.

Hand the username and the temporary password to the person in a way that is not
stored in an e-mail archive. The temporary password is not shown again. If it is
lost before the first sign-in, use **Password** on that row to issue a new one.

### Roles on the account

Assign roles from the account table, not by job title. The names on the left are
what the database, the API and this guide use; the screen shows the same roles
as words (Central Administrator, Head of Department) so an administrator is not
asked to read a code:

- `EMPLOYEE` - always present; requests leave.
- `SUPERVISOR` - approves for the people who report to them.
- `HOD` - approves for the whole department.
- `PA_CLERK` - acts for a department office.
- `DEPT_ADMIN` - administers one department.
- `CENTRAL_ADMIN` - the administrator who sees the Administration screen.
- `AUDITOR` - read-only access for audit purposes.

**Roles** opens a dialog with the extra roles already ticked. Save replaces the
whole set on that account. `CENTRAL_ADMIN` should be given to a very small number
of people.

### Reset a password

Choose **Password** on the row. A new temporary password (generated, or typed in
the dialog) is shown once and must be changed at the next sign-in. Resetting does
not end any session the person already has open, so on a shared machine ask them
to sign out.

### Disable and enable

Choose **Disable** on the day a person leaves or loses the right to use the
system. The account can no longer sign in, but all history stays readable.
**Enable** reverses a mistake; do not use it to work around a password lock - the
lock expires by itself after 15 minutes.

### Two-step signing

An account owner can add a second step at sign-in, a six-digit code from any
authenticator app, from **Two-step signing** on their own home screen (`/two-step`).
It stays off until they turn it on, so no clerk or head of department is forced to
install an app, and nothing on this screen can enrol it for them: only the person
holding the phone scans the picture and types a live code, so there is no enrol
button here at all.

- The accounts list has a **Two-step** column reading `On since <date>` or `Off`.
  It simply shows whether the account carries a seed.
- **Two-step off** on a row is the recovery for a person who cannot get their code:
  the phone is lost or wiped, they left the university with the only app, or the
  server's `LEAVE_TWOSTEP_KEY` changed so their stored seed cannot be opened
  (the `TWO_STEP_SEED_UNREADABLE` case in `OPERATIONS.md`). It clears the seed so
  they sign in with their password alone and can enrol again afterwards. The act is
  written to the audit trail as `TWO_STEP_CLEARED` with the account named.
- What it costs: it removes a protection the person asked for, so tell them, and
  they have to set it up again themselves. It is not a password reset and does not
  touch the password or the lock.
- You cannot turn two-step off for **your own** account from here. The button
  refuses with a sentence pointing you to **Two-step signing**, where you give your
  password *and* a live code from the app. That is deliberate: a signed-in browser
  is the very thing a second factor is meant to outlast, so removing it has to
  prove you still hold the phone.

### What the system checks for you

- The account is tied to an employee record, so leave balances and approvals
  always have a real person behind them.
- An administrator cannot disable their own account or remove their own
  `CENTRAL_ADMIN` role, so the last administrator cannot lock the system.
- Every create, reset, role change and disable is written to the audit trail
  with the administrator's username.

## Roles

- Create an account only after HR confirms employment.
- Assign the supervisor and head of department explicitly; do not rely on job
  titles.
- Grant PA or clerk rights per department, not university-wide.
- When the HOD changes, set an acting HOD with an end date so approvals do not
  stop.
- On departure, disable the account the same day; history remains.

## Passwords and sign-in

- A new account is created with a temporary password and must be changed at the
  first sign-in. Until it is changed, every other screen is blocked and only the
  change-password screen and "Sign out" are offered.
- Password rules: at least 12 characters, at most 72, not a common word, not
  containing the username, and different from the last five used.
- Five wrong passwords lock the account for 15 minutes. Nothing is unlocked by
  hand; the lock expires on its own. Do not re-enable an account to work around
  a lock.
- Every sign-in screen shows a **security code**: six characters in a picture,
  which the person types above their user name and password. The code is checked
  on the server before the password is looked at, so a program that is guessing
  passwords cannot work without reading the picture each time. It is the same
  kind of code the earlier sign-in used.
  - If the picture is hard to read, click it or press **New code** for another.
  - A code is used once. A wrong password, a wrong code or five minutes of
    waiting means the next attempt needs a new one, so tell a user who is
    complaining that "it will not let me in" to retype the code that is on the
    screen now, not the one they saw a minute ago.
  - Too many wrong codes, or too many pictures asked for, and the picture is
    replaced by a box that says `Too many codes asked for. Try again in N
    seconds.` Nothing is broken and no account is locked: it is that **browser**
    resting for two minutes. Tell the user to wait for the seconds shown and then
    sign in as usual - clicking **New code** during the wait only reprints the
    same wait. The account lock above is a separate thing and counts wrong
    passwords, not codes.
  - What is counted, and what an administrator can change: 120 pictures a minute
    from one address, 40 pictures for one browser, 3 wrong codes for one browser
    and a 120 second wait, as `app.sign-in.code-pictures-per-address-per-minute`,
    `code-pictures-per-session`, `wrong-codes-per-session` and
    `wait-after-too-many-seconds` in `application.yml`. Leave them alone unless
    the whole campus reaches the platform through one address: behind a single
    shared proxy the 120 is a shared budget and the first symptom is offices
    being told to wait for no reason. Raising that one number is the fix.
  - Nothing to configure and nothing to turn off: the screen refuses an attempt
    that has no code, and the refusal says so rather than saying "wrong
    password".
  - The server keeps a scrambled form of the answer in the visitor's own session,
    never the answer, and the key it scrambles with exists only while the
    application runs. A restart therefore retires the codes already on people's
    screens; they type the next picture and nothing else is affected. There is no
    key to store, rotate or lose.
- Some accounts show one more box on the sign-in screen: **Code from your app**,
  labelled "Only if you use two-step". It is empty and ignored for a person who
  never turned two-step on. For one who did, a wrong app code is refused before
  the password is looked at and counts toward the same five-in-fifteen-minutes
  lock as a wrong password, so tell someone who keeps failing that the code is the
  live six digits their app is showing now, not one from a minute ago; and if
  their phone is gone, clear it for them with **Two-step off** above rather than
  resetting the password, which does not touch the second factor.
- Sessions end after 30 minutes of inactivity and after eight hours in total.
- A password change does not yet end the user's other open sessions; tell the
  user to sign out on shared machines.

## Leave decisions

- The applicant may withdraw a request while it is still waiting for the
  supervisor or the HOD. Withdraw it instead of leaving it to be rejected: the
  request stays in the history marked **Withdrawn by applicant** and nothing is
  taken from the balance. Once a decision exists the withdraw button disappears
  and only an administrator can correct the record.
- **Approve as compensatory leave** grants the days without deducting them from
  the balance. It is for the cases where the time was earned back, such as duty
  on a public holiday or a weekend. The confirm box states that nothing will be
  deducted, the row is marked `compensated`, and the audit entry says the same,
  so a register can tell the two kinds of approval apart. Never use it to get
  around a short balance - that is what a rejection and a correction are for.

## The bell

- A bell sits in the header of every screen, beside the navigation. A number over
  it says how many messages have not been read; there is no number when there is
  nothing to read, and a hundred unread shows `99+` rather than pushing the header
  wider than the page.
- Opening it lists the messages newest first, each with the day and time, the
  words **not read yet** while it is unread, and its own **Mark read** button.
  **Mark all read** clears the number and leaves every message on the list - a
  message is never thrown away by reading it. One sentence above the list says how
  many are shown and how many still need reading.
- A message that is about a request waiting for you, recommended to you, or taken
  back opens the Approvals queue. A message that is the answer to your own request
  opens My leave. The box closes as the screen changes, so it never sits over the
  list you just asked for.
- Who is told what: an application goes to the applicant's own supervisor; a
  supervisor's recommendation goes to the heads of that department; a decision or
  a withdrawal goes to the applicant and to whoever's queue it left. An action
  never puts a message in the bell of the person who took it, so your own approvals
  do not pile up in front of you.
- Where there is nobody above an employee - no supervisor, or a supervisor with no
  sign-in account - the messages go to the central administrators instead, so a
  request is never left with no one told. If a department's head keeps an account
  that is disabled, that department's requests arrive in the central bell; the
  fix is the account, not the leave module.
- A message is read when a screen opens, so one that arrives while a screen is
  already open appears on the next screen or the next time the bell is opened -
  the platform does not watch for it in the background.
- This is the only place a message is kept by default. E-mail is a separate
  switch on the Administration screen and is off until somebody turns it on;
  with it off nothing leaves the platform and the bell is the whole story.
- A message carries a staff name, the leave type, the dates and any remarks the
  approver typed. It never carries a password, a hash or a security code, and
  nobody but its recipient can read it: the address is taken from the session, not
  from what the page asks for.

## E-mail

The platform does not need any of this to work. The bell already carries every
message; this is for the administration wanting the same news in the person's
own inbox as well. Nothing is typed into a file and nothing is hard-coded: the
university's mail details go in through this screen when somebody is given them.

- The screen is a card on **Administration**, below the sign-in accounts.
  Only a central administrator can open it or its endpoints.
- **Send mail** is the switch. Filling in a server name does not start anything;
  mail leaves the platform only while that box is ticked.
- What the mail server needs: **Mail server** (e.g. `mail.example.edu`),
  **Port** (25, 587 or 465 - ask IT), **Sign-in name on the mail server** and
  **Password** when the server asks for one, **Sender address** (the address the
  mail is from, e.g. `leave@example.edu`), and **Use TLS** for a server
  that wants STARTTLS. A plain relay inside the campus network usually needs no
  sign-in name, no password and TLS off.
- **Mail these kinds of message** chooses which news is e-mailed. Leaving all of
  them unticked means "all of them, the same ones the bell shows", so turning
  the switch on without touching the boxes works as expected. Tick only
  **Request decided** to mail people the answer about their own leave and keep
  the queues in-app.
- The password box is write-only. After a save it goes blank again and the note
  under it says only whether one is stored; leave it blank to keep the stored
  password, type a new one to replace it, and there is no way to read an old one
  back out of the screen.
- **Send a test** answers with what happened, in words: `Sent.`, `Not sent: Mail
  sending is switched off.`, or `Failed:` followed by the mail server's own
  sentence. Use it before ticking **Send mail**, because a wrong port and a wrong
  password look the same from the outside.
- The list under it is the last fifty attempts, newest first, with the reason for
  every one that did not go out. This is the first place to look when
  somebody says they had no e-mail: a message with no address on their staff
  record is listed as `not sent - No e-mail address is saved for that person`.
- One thing has to be done by the person who runs the server before a password
  can be stored: the environment variable `LEAVE_MAIL_KEY` must be set in
  `/opt/leave-platform\.env.local`. It is the text that seals the password, and it is
  deliberately not in the database. The screen says so while it is missing, and
  everything except the password still saves. Changing it later means the stored
  password has to be typed again - which is the point.
- Only an address already on the person's staff record is used, and only for an
  account that is still enabled. The platform never guesses an address from a
  username, and a disabled account is not mailed.
- A mail failure never affects the leave request. The message is already in the
  bell and the decision is already saved; the log is the only place that knows
  the mail server was unreachable.
- Every save of these settings, and every test send, is written to the audit
  trail (`MAIL_SETTINGS_UPDATED`, `MAIL_TEST_SENT`) with the names of the fields
  that changed - so "who pointed our leave mail at somebody else's server" has
  an answer. The password itself is never in an audit entry.

## Help desk

Anyone who can sign in can ask the administration office for help without
walking to a room or sending a mail from a personal account. The person uses
**Help desk** in the header; the office answers from the **Help desk** card on
**Administration**, just above **Record corrections**.

- What a person fills in: **What do you need?** (their leave record, a sign-in
  or account problem, something not working, or anything else), one line saying
  what is wrong, and optionally the details - dates, numbers, what they have
  already tried. The screen tells them never to type a password or a security
  code there, and the words "never type a password" are repeated on the desk's
  own answer box.
- Each ticket gets a number, `HD-000042`, which both sides quote. The number is
  written by the database as the ticket is created and cannot be typed or
  changed by anybody.
- The state says whose turn it is, and that is the only thing the state means:
  **Waiting for the desk** and **Being handled** both mean the office owes an
  answer; **Answered, waiting for you** means the person who asked owes the next
  word; **Closed** means it is finished.
- The office's card lists what is waiting, longest-waiting first, with a count
  above it. Ticking **Show every ticket, including closed ones** puts the whole
  list on screen, which is where a reopened matter is found.
- **Hand it to** + **Assign** puts one name on the ticket. This is the way to
  stop two people answering the same question, and the box only offers accounts
  that can actually sign in and answer - a name typed from memory is not
  offered. Nobody is told about a line they wrote themselves.
- **Send answer** writes as the university. **Close ticket** finishes the matter,
  with or without a last line, and tells the person who asked either way.
- A closed ticket cannot be reopened by the office - only the person who asked
  can bring it back, with the button **Reopen this ticket**. That keeps a
  decision from being made twice by two different screens, and it is the office's
  answer to "it has come back": close it and let them say so.
- Everything said on a ticket stays as it was said. Nothing in the platform
  edits or deletes a line, so the conversation itself is the record of who said
  what and when.
- The office's three decisions are written to the audit trail
  (`TICKET_ANSWERED`, `TICKET_ASSIGNED`, `TICKET_CLOSED`) with the ticket number,
  so "who answered as the university" has an answer. A staff member opening a
  ticket or writing in it is not audited - the thread already keeps it.
- The bell carries every step, and e-mail follows whatever kinds the switch on
  this screen was given. **A help-desk message opens the Help desk screen, never
  a leave screen**: a ticket id travels in the same column a leave message uses,
  so if a ticket message opened **My leave** it would show somebody else's dates.
- A colleague who guesses a ticket number cannot read it. Only the person who
  asked and the office can open a conversation.

## Record corrections

A member of staff who finds something wrong on their own record - name, staff
number, designation, e-mail or phone - proposes the right value on **My record**
in the header; the office decides from the **Record corrections** card on
**Administration**, the last one.

- What a person fills in: the fields they want changed, with the value they
  want each to hold, and a line saying why. Fields they leave alone stay off
  the request; there is no tick to check. A person cannot ask for a field to be
  emptied - removing a staff number or an e-mail is done here, in the employee
  editor, after a help-desk question if needed.
- Each request gets a number, `PR-000025`, which both sides quote. The database
  writes it as the row is created; nobody can type or change it.
- One person can have one request waiting. A second attempt is refused with a
  sentence pointing at the first, so the office is never asked the same
  question twice at once.
- Asking for a value the record already holds is refused when it is typed, not
  when it is decided: "Your phone already is that number" saves the person a
  wait and the office a row.
- The card lists what is waiting, longest first, with a count above it. Ticking
  **Show every request, including answered ones** lists what has been answered
  too. **Open** a row to see, per field, what the record says now beside what
  was asked for. The current value is read live each time the panel is drawn,
  not from the request - so a record somebody corrected by hand in the meantime
  is visible before deciding, not after.
- **Approve and correct the record** writes exactly the fields the request
  names and nothing else, and re-checks each value on the way: a staff number
  that has gone to somebody else since the request was made is refused here
  with a sentence, not on a database constraint. The record edit itself is
  visible in the employee editor afterwards, because the approval changes the
  employee row through the same rules that editor enforces.
- **Refuse** must say why - the box insists - and the person is sent those
  exact words in the bell. A refusal with no reason is a dead end for the
  person, which is the whole thing the feature is trying to avoid.
- The person can **Withdraw** their own request while it waits; nothing is
  changed and the queue simply loses the row.
- Decisions are audited (`PROFILE_REQUEST_APPROVED`, `PROFILE_REQUEST_REJECTED`)
  with the request number; proposing and withdrawing are ordinary business and
  are not.
- The bell carries every step: the desk hears when a request arrives, the
  person hears the answer. E-mail follows whatever kinds the switch on this
  screen was given, like everything else.

## Delegated approving

A supervisor or a head of department who is going away for known days hands
their approving to a named colleague on **Delegations** in the header, for a
window with a first day and a last day. Nobody is ever given anybody else's
password: the colleague signs in as themself, the requests arrive in their own
queue, and every trace says whose authority was being used.

- Two kinds of window, and a person can hold one without the other.
  **Recommending** covers the requests of the staff who report to the
  delegator - so only someone with direct reports can hand it out. **Final
  decision** covers the department's decisions, so only a head of department
  can hand it out, and the department is fixed when the window is written:
  a transfer afterwards does not move an old window's authority.
- Each window gets a number, `DL-000007`, which both sides quote. The database
  writes it as the row is created; nobody can type or change it.
- The colleague must be on the active staff with a working sign-in account.
  A window handed to a person who cannot sign in would reach nobody, so the
  screen refuses it while the delegator is still typing.
- A window cannot start in the past and cannot reach further than 366 days. A
  step that must be handed over for longer than a year is not an absence, it
  is a change of reporting line - make it on the staff record, where the
  reporting line actually lives.
- One unhanded-back window per person per step: a second overlapping attempt
  is refused by name and number ("You already have Recommending delegated to
  Ali Ahmed from ... (DL-000003)"), because two windows would put one queue in
  two pairs of hands.
- The window closes itself at midnight after its last day. Nothing to remember,
  no job to run. **Close now** ends it early and belongs to the person who
  opened it - they may be the one now sitting at their desk - or to this office.
  The colleague holding it cannot close it: giving back an authority was never
  the holder's act to do.
- A central administrator's university-wide authority is deliberately not
  delegable. It is the fallback for the whole platform, including these very
  screens. If a department has no head to decide while someone is away, that is
  a missing head of department, and it is fixed on the organisation screen.
- The office sees every window, both sides named, on the **Delegated
  approvals** card on **Administration** (read-only there; closing is done by
  the person who opened it or through their own screen).
- The bell carries every step: the colleague hears when a window is handed to
  them, both sides hear when it is closed, and the delegator is told when
  somebody actually decides a request under their window - with the request and
  the `DL-` number. Actions taken under a window are audited as the delegatee's
  own, with "acting for &lt;name&gt;, DL-000007" beside them, so the trail
  never names a person who was away.

## Leave configuration

Reached from **Leave setup** in the header; only a `CENTRAL_ADMIN` account sees
it. It holds the two lists that used to need a database edit.

- **Public holiday calendar.** Pick the year, then add a date and a name - for
  example 23 March, Republic Day. A holiday is a calendar day: it is skipped
  only when it falls Monday to Friday, and a range that turns out to be nothing
  but holidays is refused rather than filed as a zero-day request. The same date
  cannot be listed twice, and a name longer than 150 characters is refused.
  Removing a day is confirmed, not silent.
- Editing the calendar does not re-count requests that were already submitted.
  Each request keeps the days the server counted at the time, so a mistake is
  corrected by withdrawing and re-applying, or by a rejection plus a balance
  correction - never by editing history. Both adding and removing are written to
  the audit trail (`HOLIDAY_ADDED`, `HOLIDAY_REMOVED`).
- **Balance corrections.** Search the staff number or name, open **Balances** on
  the person, then **Correct** the row for the year and leave type. The dialog
  shows how many days are already used and refuses a grant below it, so nobody
  can be left with a negative balance. A reason is mandatory - quote the HR
  order - and the entry is audited with the old and new numbers
  (`BALANCE_ADJUSTED`). A correction is not an approval: it changes what the
  person may apply for, nothing else.
- Implemented: one leave type (Casual Leave, 25 days a year), the university
  holiday calendar read by the day counter, and corrections of a single
  employee's yearly balance.
- Planned, not built yet: more leave types with their own entitlement,
  carry-forward and attachment rules; per-department holidays; the year-end
  reset. Changing a type does not rewrite past balances.

## Year end (planned)

1. Confirm the new year's holiday calendar.
2. Run the carry-forward preview and review staff with balances above the cap.
3. Approve the preview; the system creates the new balances and records the
   calculation.
4. Keep the previous year readable.

## Reports and exports

Three reports exist, on the **Reports** screen (`/reports`) and over the API (see
**Narrowing a list** below for the same period rules the screens use):

- **Leave register** - one line per request, newest first, with the request's own
  number, who asked, for which dates, how many days, what happened to it and what
  was written on it. `GET /api/v1/reports/register` answers as a list;
  `GET /api/v1/reports/register/csv` gives the same rows as a spreadsheet file.
- **Staff summary** - one line per active employee for one year: the days
  granted, the days used, what is left, the days already approved, the days
  still waiting and how many requests the person made.
  `GET /api/v1/reports/staff-summary` and `.../staff-summary/csv`.
- **Monthly attendance sheet** - one line per active employee and one cell per
  day of one month, with the totals beside them.
  `GET /api/v1/reports/attendance` and `.../attendance/csv`. The cells say:
  **W** weekend (Saturday or Sunday), **H** a day in the holiday calendar,
  **CL** approved casual leave, **CL\*** approved as compensatory, **?** a
  request covering that day that nobody has decided yet, and **blank** for a day
  with nothing recorded. A blank is not a mark of presence: the platform holds
  leave requests, not attendance, and the sheet says so on screen and in its own
  legend line.

- **Printed application forms** - not a report, paper. Tick the requests on the
  register and press **Print the application forms for the rows ticked below**:
  the browser opens `GET /api/v1/reports/iso-form?ids=7,9,11` on a page of A4
  sideways holding two `F/SOP/REG 01/21/02` forms per sheet with a dashed cutting
  line between them. Tick up to 50 at a time; a pile of 50 is 25 sheets, and the
  toolbar above the paper says both figures so an office can see what the printer
  is about to use. The toolbar, and the credit line in it, are marked
  `no-print` and never reach the sheet.

What an administrator needs to know about them:

- Only a **central administrator** and an **HOD** can read a report. An HOD gets
  their own department and nothing else - naming another department number is
  refused, not quietly answered. A central administrator may name one department
  or name none, which means the whole university.
- The **register** takes the same period and status questions as the lists on the
  other screens, and a request belongs to a period when it **overlaps** it. The
  **summary** takes a year, and the **attendance sheet** a year and one month.
- The screen register stops at the newest 200 rows. **A file does not stop**: it
  reads on until the data runs out, because a spreadsheet that quietly stopped
  short would understate a department's year. The summary and the attendance
  sheet are one line per employee, so the screen shows all of them and the two
  figures cannot disagree.
- The attendance sheet reads the **holiday calendar** for its H marks. A weekend
  or a holiday is marked as that even when a request spans it, and it is left out
  of the "Approved Days" total beside the cells, because the day counter never
  charged those days either. The cells and the total therefore agree with each
  other, and neither one counts a Saturday as leave.
- Every download is written into the audit trail as **Report exported** with the
  report, the department or "the whole university", the period chosen and how
  many rows were handed over. When someone asks who took a list of other
  people's leave, that is the entry to look at.
- An employee with no approved leave yet still appears on the summary and on the
  sheet with their full allowance, rather than being missing from a sheet
  somebody signs.
- On the screen the **Reports** link appears for a central administrator and an
  HOD only. A central administrator gets the department box - empty means the
  whole university - and an HOD does not get the box at all, because the service
  would refuse any department but their own. The download under each list asks
  for what that list currently shows, so the file and the screen cannot tell two
  stories.

- A printed form proves itself. Beside the request's `LV-` number it carries a
  twelve-character code, made by this server's `LEAVE_VERIFY_KEY`, and the QR is a
  picture of the same pair (ADR-019). Anyone with the sheet can check it at
  `GET /api/v1/reports/verify?reference=LV-000123&code=...` - no account, no
  sign-in - and the answer is only what the form already states plus the status
  as it stands today. A number that is not there, a code that is wrong and a pair
  that is badly shaped are refused in the same sentence, so the endpoint cannot
  be used to find out which half someone guessed.
- **The public check is counted, and the count is per address.** One address may
  ask 60 checks in a minute (`app.verify.checks-per-address-per-minute`); after
  that it is answered `429` with a `Retry-After` and a sentence that names no
  person, no leave and no reason, and the count opens again by itself a minute
  later. A visitor who is told to wait while checking a stack of forms is either
  a script walking a number range or one office behind one shared proxy; the
  second is the only reason to raise the number.
- **Set `LEAVE_VERIFY_KEY` or say so out loud.** A server without it still prints
  forms, but with the number in letters, no code, and a line on the paper saying
  this server cannot verify forms; every check then answers 503. Do not put a
  key in the repository or copy one server's key to another: forms printed by one
  server must not verify on another, and a shared default key is a public one.
  Changing the key invalidates every code already printed, so change it only when
  the office can reissue what is in circulation.
- The reason an applicant typed can run to 1000 characters; the paper cannot hold
  that inside its box, so the form prints the words that fit, stops at a whole
  one and says the rest is in the leave register under that number. The register,
  the screen and the spreadsheet still hold every word, so nothing is lost - only
  the sheet is shorter than the claim.

- **A person's year prints as one sheet each.** Ticking names on the staff
  summary and pressing *Print the leave records for the names ticked below*
  gives one A4 upright page per person: the year on the paper, the granted, spent
  and left figures beside the requests that produced them, and every request
  whatever was decided about it - approved, still waiting, refused, withdrawn -
  with the days of a period printed from its first day to its last. Two signature
  lines close it. The sheet says what it is not: it is not an attendance
  register, so a day with no line on it is a day nothing was asked for, and the
  balance is stamped with the day the page was printed rather than 31 December,
  because that is the only day the platform can speak for. One run takes at most
  fifty names, which is a pile an office signs, not a whole university.

A large export is still produced in one go; the queue in ADR-009 comes when the
volume needs it.

Implemented today: the audit trail screen (`/audit`, newest 200 entries with a
search box and a period) and the per-account screens on `/admin`.

## Narrowing a list

Every list on **My leave**, **Approvals** and **Audit trail** can be narrowed, so
nobody has to scroll a year of university leave to find one week.

- **From** and **To** are the period. Leave either blank: a blank box means "any
  date", so From alone means "from that day onwards" and To alone means "up to
  and including that day".
- A request appears when it **overlaps** the period. Leave from 30 June to 3 July
  is found by a July search, because those are days the department was short.
- **My leave** also has a **Status** box (waiting for supervisor, waiting for HOD,
  approved, rejected, withdrawn). **Approvals** has no status box on purpose: both
  of its queues only ever hold waiting requests, so a status there would either
  empty a queue or change nothing.
- **Show everything** puts every box back to blank and reloads the full list.
- The line above the list states what it matched and how many rows, and says so
  when the newest 200 were all it could show - narrow the dates to see the older
  ones. A list that stops without saying so is read as "this is everything".
- Choosing a first day closes the earlier days in the second box, and the other
  way round, so an impossible period cannot be clicked. Asked over the API it is
  refused with a clear sentence instead of an empty list.

## Audit review

Open **Audit trail** in the top bar (the central administrator and the auditor
both see it). The screen shows the latest 200 entries, newest first, with the
time, who did it, the action, what it was done to and a readable summary.

- The action and the "On" column are written as words - "Leave approved",
  "Public holiday" - because a code is not something to read on a screen. The
  database still holds the code, and the search box accepts either spelling:
  `leave approved` and `LEAVE_APPROVED` find the same entries.
- Type in **Search** and press Enter to filter by person, action or any word in
  the summary; use **From** and **To** for a date window - an access review of one
  week does not need the whole trail; **Show everything** clears both.
- Each sign-in is recorded, so the trail also answers "who was in the system".
- Review privileged actions monthly: role changes, password resets, account and
  employee changes, department changes and leave decisions.
- The auditor role is read-only and cannot change data. Give it to a person who
  needs to check work, not to someone who administers.
- Entries are written by the same transaction that makes the change, so a
  refused or failed action leaves no entry and a successful one cannot lose it.
- Entries are never edited or deleted from the application. Keep the database
  backup so older entries survive; exporting the view for the quarterly access
  review is planned.

## What not to do

- Do not share accounts or passwords.
- Do not use the database `root` account (or Oracle `SYSTEM`/`SYS`) for the
  application.
- Do not delete rows directly in the database; use the application so the audit
  trail stays complete.
- Do not change department codes after data exists.

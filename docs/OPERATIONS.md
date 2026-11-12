# Operations

## Daily checks

- `/actuator/health` returns `UP` (database and disk space included).
- Error rate in the application log; look for repeated authentication failures.
- Whether anybody was told to wait. A `429` on `/api/v1/auth/captcha` or
  `/api/v1/reports/verify` is `limit/RequestThrottle` refusing a caller: a few
  an hour is a script being a script, but a person who reports the
  `Too many codes asked for.` box is either guessing codes - which costs them
  two minutes and nothing else - or the whole building is arriving through one
  address, which is the case for raising `LEAVE_CAPTCHA_PICTURES_PER_ADDRESS`.
- Two-step signing (feature 49). A `TWO_STEP_SEED_UNREADABLE` line in the log
  means this server cannot open the authenticator seeds stored under
  `LEAVE_TWOSTEP_KEY`, which happens when that key was changed or lost after people
  enrolled; those accounts then cannot sign in at all until the key is put back.
  If the key is genuinely gone the recovery is the administration team turning
  two-step off for each account and the person enrolling again (see
  `ADMIN-GUIDE.md`), not a password reset, which does not touch the second factor.
- A sudden burst of account locks can now also come from wrong app codes, not
  only wrong passwords: a refused code counts into the same five-per-fifteen-
  minutes lock (`AppUser.registerFailedAttempt`). A person who changed phone and
  is still typing the old device's code will trip it, and one locked account in a
  burst is usually that, whereas a burst across many accounts is the guessing the
  code check exists to stop.
- Background job queue depth; alert if it grows for more than 15 minutes.
- Disk usage below 85 percent on the data, log and backup volumes.
- Last night's backup completed and its checksum record exists.

## Monitoring and alerts

| Signal | Warning | Critical |
| --- | --- | --- |
| Health endpoint | 1 failed check | 3 consecutive failures |
| HTTP 5xx rate | above 1 percent for 5 minutes | above 5 percent |
| Login failures | 20 per minute from one address (wrong passwords or wrong app codes, which share one lock) | sustained attack pattern |
| Two-step seed unreadable | any `TWO_STEP_SEED_UNREADABLE` line | enrolled accounts cannot sign in |
| Queue depth | above 100 jobs | above 1,000 or oldest job older than 30 minutes |
| Disk usage | above 80 percent | above 90 percent |
| Backup age | above 26 hours | above 48 hours |
| Certificate expiry | 30 days | 7 days |

Alerts go to the IT monitoring system and the named maintainer.

## Backup

- Nightly encrypted logical backup of the `LEAVEAPP` schema with `expdp`
  (`ENCRYPTION=ALL`), plus the file storage directory.
- Backups are copied off the application server; the application account cannot
  delete them.
- Keep 30 daily, 12 monthly and 7 yearly copies, or as IT policy requires.
- Record the backup name, size, checksum and completion time in the operations
  log.

## Restore drill (quarterly)

1. Choose the latest backup and its checksum.
2. Restore with `impdp` into an isolated schema, for example
   `LEAVEAPP_RESTORE_TEST`, remapping the schema on import.
3. Compare row counts for users, applications, balances, holidays and audit
   events.
4. Start a staging application against the restored schema and run the smoke
   test.
5. Record the result, duration and any differences. Drop the test schema.
6. If the drill fails, raise it as an incident and repeat after fixing.

## Common incidents

### Application does not start

Check, in order: service status and logs, environment file present and readable,
database reachable, database credentials valid, Flyway migration state, disk
space, port already in use. Do not disable migrations to force a start in
production.

### Database unavailable

The application returns 503 for API calls. Keep the application running; it
reconnects when the database returns. Record the outage window for audit. If
data corruption is suspected, stop writes and restore from the last verified
backup.

### Disk full

Free space by rotating logs and clearing temporary report files; never delete
audit rows or the last backup. Investigate the cause before resuming.

### Mail relay failure

Notifications queue rather than fail. Confirm the queue drains after the relay
returns; notify staff through normal channels if the outage is long.

### Report queue backlog

Check worker logs and the slowest query. Temporarily raise workers only after
confirming the database has capacity; large exports are rate limited per user.

### Suspected security incident

Follow `SECURITY.md`: contain, preserve evidence, notify, recover, review.

## Patching and change management

- Critical security patches within 7 days, high within 30 days.
- Changes go through a pull request, CI and staging.
- Production changes have a rollback plan and a maintenance window when the
  schema changes.
- Every deployment is recorded in `CHANGELOG.md` with date and author.

## Scaling up and down

- **Add a department:** administrator action in the application; no deployment.
- **Add a faculty or office:** administrator action; codes are permanent.
- **Merge or close a department:** administrator action with a successor
  recorded; history remains readable.
- **Capacity:** measure before adding hardware. The first levers are indexes,
  report queue workers and database memory, then a larger virtual machine.

# Security Policy

## What this platform is

A leave-management application for an institution: staff records, approvals,
balances, an audit trail. It holds names, designations, contact details and the
dates people are away. Treat a deployment of it as sensitive, and read
[`docs/SECURITY.md`](docs/SECURITY.md) for the design — including the risks the
current version accepts, which are listed at the end of that file rather than
buried.

## Reporting a problem

Please do not open a public issue for anything that could be used to read or
change someone's data.

1. Describe it in a **draft security advisory** on this repository:
   Issues → Security → Report a vulnerability. That is the private channel, and
   it lets the fix ship before the description becomes public.
2. If you would rather not use an advisory, open a normal issue that says only
   "security concern — please contact the maintainers" and hold the detail back
   until someone replies.

Include what you tried, which version (the tag, or the commit you built), and
what you would need to be able to do to make it a problem. Redact real names and
records: a report that carries somebody's staff data becomes a second incident.

## What to expect

There is no paid bug-bounty and no service-level commitment on replies. No
funded support arrangement sits behind this repository, and a deployment for a
real institution stays a local responsibility. You should expect an
acknowledgement when a maintainer sees it, and a fix or an honest "this is by
design, here is why" — the accepted-risk list above is where the "by design"
answers already live.

## Hardening a deployment

Before you put real people on it:

- Serve it over HTTPS and set `LEAVE_COOKIE_SECURE=true`.
- Set `LEAVE_MAIL_KEY`, `LEAVE_VERIFY_KEY` and `LEAVE_TWOSTEP_KEY` to long random
  values held outside the repository and outside the database. Losing the
  two-step key means every enrolled person sets their authenticator up again;
  losing the others turns off mail and the printed-code check.
- Keep `LEAVE_DB_POOL_SIZE` and the per-address ceilings in
  [`.env.example`](.env.example) unless you have a reason written down.
- Put a real reverse proxy in front of it and leave
  `forward-headers-strategy: native` as it is — the sign-in and form-check
  ceilings are keyed on the client address, and that address is only honest if
  the proxy is on an internal network.
- Replace the invented organization that `V006` loads with your own structure,
  and delete the sample rows you do not use.
- Back up before every deploy, and rehearse the restore at least once
  ([`docs/DATABASE.md`](docs/DATABASE.md), "Backup" and the restore drill in
  [`docs/OPERATIONS.md`](docs/OPERATIONS.md)).

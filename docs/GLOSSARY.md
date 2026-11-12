# Glossary

| Term | Meaning |
| --- | --- |
| Legacy applications | The single-department systems this platform is meant to replace |
| Wing | A faculty, department or administrative office |
| Faculty | Top-level academic group containing departments |
| Department | Academic unit or administrative office; identified by an immutable code |
| Department code | Short unique reference such as `MTE`, used by imports and reports |
| Scope | The department boundary applied to a user's data access |
| HOD | Head of Department |
| Delegated approving | A dated window in which a named colleague may act for a supervisor at the recommending step, or for a head of department at the final decision. The colleague signs in as themself; see ADR-018 |
| Window (delegation) | The pair of days a delegation covers: the first day a colleague may act and the last. A window expires by comparison with today's date, needs no job to close it, and can be ended early only by the person who opened it or the office |
| Delegation scope | Which approving step a window hands over - `SUPERVISOR` (recommending) or `DECISION` (a department's final decisions). Note this is a second meaning of *scope*; the row above is about data access |
| Request number | The number one leave request is quoted by everywhere: `LV-000123`, written by the database from the row's id and never typed. Also called its reference |
| Application form | The university's own paper, `F/SOP/REG 01/21/02`, which the platform fills and prints: two forms to a sheet of A4 sideways, cut apart, one per request |
| Print run | The rows ticked on the register, or the names ticked on the staff summary, put on paper together, at most 50. A form run is audited as `LEAVE_FORM_PRINTED` quoting every number that went out; a record run as `LEAVE_RECORD_PRINTED` quoting the year and every name |
| Leave record | One person's year on one sheet of A4 upright paper, printed from the staff summary: the granted, spent and left figures beside the requests that produced them, every request whatever was decided about it, and two signature lines. It is a leave history, not an attendance register - a day with no line on it is a day nothing was asked for |
| Verification code | The twelve characters beside a form's number, and the same pair in its QR, which let somebody holding the paper confirm it at `GET /api/v1/reports/verify` without an account. Made by HMAC-SHA256 of the number with `LEAVE_VERIFY_KEY`; see ADR-019 |
| `LEAVE_VERIFY_KEY` | The server's signing text for verification codes. Set per server by the administration team, never committed and never defaulted; without it the platform mints no code, refuses every check and prints forms that say so in words |
| PA | Personal Assistant, a role that records applications for staff |
| BPS | Basic Pay Scale, the civil-service grade used in government pay structures |
| LWP | Leave Without Pay |
| Leave balance | Remaining entitlement for an employee and leave type in a year |
| Carry-forward | Unused balance moved to the next year, subject to a cap |
| Audit event | Immutable record of who changed what, when and from where |
| Flyway | Tool that applies versioned database migrations |
| Migration | A versioned SQL change applied in order |
| Spring Boot | Java application framework used for the backend |
| JPA | Java Persistence API, used to map tables to Java objects |
| OIDC | OpenID Connect, an SSO protocol |
| SSO | Single Sign-On with the university identity provider |
| MFA | Multi-factor authentication |
| CSRF | Cross-Site Request Forgery, blocked with tokens on state changes |
| RPO / RTO | Recovery point objective / recovery time objective |
| UAT | User acceptance testing |
| ADR | Architecture decision record, stored in `DECISIONS.md` |
| Staging | Pre-production environment that mirrors production |

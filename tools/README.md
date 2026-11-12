# Tools

One-off utilities for importing and verifying data. Every tool must be safe to
run twice and must refuse to write to a live database.

Planned:

- `import-employees` - CSV/HR feed import with dry-run and error report.
- `verify-migration` - compare row counts and balances against the source dump.
- `department-seed` - generate the official department list from the HR export.

Rules:

- Read credentials from the environment; never hard-code them.
- Default to dry-run; writing requires an explicit flag and a non-production
  database name check.
- Log a summary, not personal data.

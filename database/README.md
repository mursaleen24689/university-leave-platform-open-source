# Database assets

Schema changes are applied by Flyway from
`../backend/src/main/resources/db/migration`. This directory holds supporting
material only.

`provision-app-leave.sql` creates the `LEAVEAPP` schema and grants. A DBA
runs it once per instance, before the first application start.

Planned contents:

- `sample/` - clearly marked sample data for development.
- `checks/` - SQL used to reconcile counts after a migration.
- `reports/` - saved migration reports per environment.

Rules:

- Never place a production dump or personal data here.
- Never edit a migration that has already run; add a new version.
- Import tools read a read-only export of the old system and write only to the
  `LEAVEAPP` schema.

See `../docs/DATABASE.md` for conventions and the migration procedure.

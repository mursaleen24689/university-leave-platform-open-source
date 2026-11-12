# Operations assets

This directory holds everything needed to run the platform on a server. It is
not part of the application build.

Planned contents:

- `backup/` - nightly backup script and checksum record format.
- `restore/` - restore drill script and evidence template.
- `service/` - systemd unit and Windows service definition.
- `proxy/` - Nginx or Apache virtual host with TLS.
- `monitoring/` - health check and alert rules.

Deployment is documented in `../INSTALL.md` and operations in
`../docs/OPERATIONS.md`. No scripts ship here yet: anything added is tested on
staging before it touches production.

Rules:

- No credentials in this directory. Scripts read from the environment file or
  the secret store.
- Backups are encrypted before leaving the server.
- Every script has a short header stating purpose, inputs, outputs and owner.

# Install and run

The steps to get this platform on a machine of your own, from an empty database
to a signed-in browser.

## What you need

| Thing | Version | Why |
| --- | --- | --- |
| JDK 21 | any distribution | Vaadin 25 needs Java 21; the build fails on Java 17 |
| Node.js | 24 or newer | only to build the browser bundle — development start and `-Pproduction`. A server running the production JAR needs no Node |
| Oracle Database Free | 23ai or 26ai | the application schema. 19c/21c Express Edition also work; the schema avoids features newer than 12c |
| Maven | none required | `backend/mvnw` is the wrapper and fetches what it needs |

A JDK, not a JRE: with only a JRE on `JAVA_HOME` the build stops with
`No compiler is provided in this environment`. Point `JAVA_HOME` at the JDK
before anything else — the Maven wrapper builds against whatever Java it finds
there, not against the version this file mentions.

On Windows the Oracle Free download is **26ai Free**
(`oracle-ai-database-free-26ai-23.26.3.windows.x64.zip`); the 23ai Free build is
Linux-only.

## 1. Get a database

The application has one schema of its own, behind one database user. Oracle has
no per-application database the way MySQL does, so plan for a dedicated user
inside an existing instance — usually the `FREEPDB1` pluggable database that Free
creates.

### The container route

For a trial, a container is the quickest database:

```bash
docker run --name leave-db -d \
  -p 1521:1521 \
  -e ORACLE_PASSWORD=<choose-one> \
  container-registry.oracle.com/database/free:latest
```

or, as a `docker-compose.yml` you can keep:

```yaml
services:
  db:
    image: container-registry.oracle.com/database/free:latest
    ports: ["1521:1521"]
    environment:
      ORACLE_PASSWORD: <choose-one>
    volumes: [leave-data:/opt/oracle/oradata]
volumes:
  leave-data:
```

The image tag moves with Oracle's releases. Pin it to a version you have tested
before you use this route anywhere that matters, and note that a container
holds your leave records: back it up like any other database
([`docs/DATABASE.md`](docs/DATABASE.md), "Backup").

### The installed route

Install Oracle Database Free on the host or a database server, then make sure
`FREEPDB1` is open and you can connect to it as a DBA.

## 2. Create the application schema

Connected to `FREEPDB1` as a DBA:

```sql
CREATE USER LEAVEAPP IDENTIFIED BY "<choose-a-strong-password>"
  DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW,
      CREATE PROCEDURE, CREATE TRIGGER, CREATE TYPE TO LEAVEAPP;
```

That is the whole set of grants. Flyway creates the tables and the sequences, so
the user needs no rows to start with — and the platform never asks for DBA
rights. `database/provision-schema.sql` is the same script with notes, for the
day you want a DBA to review it before running it.

Do not create the tables by hand. The migrations under
`backend/src/main/resources/db/migration` are the definition, and a schema that
drifts from them fails in confusing ways.

## 3. Tell the application where it is

```bash
cp .env.example .env
```

`.env` holds your values and must never be committed — it is in
`.gitignore` for that reason. Spring Boot does not read the file by itself;
export it into the environment of the Java process:

```bash
set -a; . ./.env; set +a      # Git Bash / bash
```

An IDE run configuration, `docker run --env-file .env` and a systemd
`EnvironmentFile` all do the same job.

The variables worth knowing:

| Variable | Needed | Default | What it does |
| --- | --- | --- | --- |
| `LEAVE_DB_URL` | yes | `jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1` | where the schema lives |
| `LEAVE_DB_USER` | yes | `LEAVEAPP` | the schema owner |
| `LEAVE_DB_PASSWORD` | yes | empty | its password |
| `LEAVE_DB_POOL_SIZE` | no | `10` | JDBC pool size — Oracle Free caps sessions, so keep it small |
| `LEAVE_SERVER_PORT` | no | `8080` | HTTP port |
| `LEAVE_FLYWAY_ENABLED` | no | `true` | apply migrations at startup |
| `LEAVE_COOKIE_SECURE` | staging and production | `false` | send the session cookie over HTTPS only |
| `LEAVE_BOOTSTRAP_ADMIN_*` | first start only | empty | the first administrator, see step 4 |
| `LEAVE_MAIL_KEY` | to store an SMTP password | empty | encrypts the mail password saved in Administration |
| `LEAVE_VERIFY_KEY` | to print a working QR code | empty | signs the code on a leave form; without it the public check refuses everything |
| `LEAVE_TWOSTEP_KEY` | to offer authenticator apps | empty | seals each saved seed; without it nobody can turn two-step on |
| `LEAVE_TWOSTEP_ISSUER` | no | `University Leave` | the name an authenticator app shows beside the account |
| `LEAVE_PUBLIC_URL` | for a scannable QR on a phone | empty | the address a phone reaches this platform at |
| `LEAVE_CAPTCHA_*`, `LEAVE_VERIFY_CHECKS_PER_ADDRESS` | no | see `.env.example` | the sign-in and form-check ceilings |

The three `*_KEY` values live only in the environment, never in the database. A
copied schema is therefore not a set of mail credentials, not a bag of second
factors, and not the ability to mint verification codes.

## 4. Create the first administrator

The migrations load reference data only — roles, leave types, the sample
organization. No account exists until you say so, and after that there is no way
to make one without signing in. So the first start needs all of these together:

```text
LEAVE_BOOTSTRAP_ADMIN_USERNAME=<a-login-name>
LEAVE_BOOTSTRAP_ADMIN_PASSWORD=<a-strong-password>
LEAVE_BOOTSTRAP_ADMIN_EMAIL=<someone@your-institution>
LEAVE_BOOTSTRAP_ADMIN_DEPARTMENT=<a department code that V006 loads, or ADMIN>
```

`ADMIN` is the Central Administration office the sample organization creates.
Optionally set `LEAVE_BOOTSTRAP_ADMIN_NAME`, `_DESIGNATION` and `_EMPLOYEE_NO`.

Remove these from the environment as soon as the account exists. The application
ignores them once an administrator is present, but leaving a password in a file
on a server is how passwords leak.

## 5. Start it

```bash
cd backend
./mvnw spring-boot:run
```

- The first start takes minutes while Vaadin builds its front-end bundle; later
  starts reuse it.
- If the database is not provisioned yet, startup stops with a connection error.
  That is the expected order, not a bug.
- Health: <http://localhost:8080/actuator/health> (open without signing in).
- App: <http://localhost:8080/> redirects to `/login`, which wants a username, a
  password and the picture code. The start page then names the account, says in
  words what its roles allow, and offers the screens.

## 6. Build for a server

```bash
cd backend
export JAVA_HOME=<your-jdk-21>
./mvnw -Pproduction -DskipTests package
java -jar target/leave-platform-0.0.1-SNAPSHOT.jar
```

`-Pproduction` puts the compiled browser bundle inside the JAR, so the machine
that runs it needs a JDK-free Java 21 runtime and nothing else. The JAR is not
relocate-able between databases: it carries the migrations, and they run against
whatever schema it starts on.

Behind nginx or Apache, forward the real client address and keep
`forward-headers-strategy: native` (the default): Tomcat then believes
`X-Forwarded-For` only from a proxy on an internal address, which is what the
per-address sign-in and form-check counts depend on. Set
`LEAVE_COOKIE_SECURE=true` wherever the site is served over HTTPS.
[`docs/OPERATIONS.md`](docs/OPERATIONS.md) covers monitoring, incidents and
patching; [`docs/DATABASE.md`](docs/DATABASE.md) covers backup and the restore
drill.

## 7. Upgrading

Flyway applies new migrations at startup and refuses to run over a schema whose
history it does not recognise. So: back up, deploy, start, check
`/actuator/health` and one sign-in. Never edit a migration that has already run
somewhere — add a new one.

## 8. Problems people actually hit

| Symptom | Cause and fix |
| --- | --- |
| `No compiler is provided in this environment` | `JAVA_HOME` points at a JRE or at nothing. Point it at a JDK 21. |
| Build fails with a class-version error | The JDK on `JAVA_HOME` is older than 21; check with `"$JAVA_HOME/bin/java" -version`. |
| Startup stops on a connection error | Database not up, wrong `LEAVE_DB_URL`, or the schema user does not exist yet. |
| Startup stops with a Flyway checksum error | Someone edited a migration that had already run. Revert the edit and add a new migration. |
| The sign-in page never shows the picture code | `GET /api/v1/auth/captcha` is being blocked — by a proxy, or by the per-address ceiling after a script ran against it. |
| Two-step enrolment says it is unavailable | `LEAVE_TWOSTEP_KEY` is not set. Deliberate: the platform will not store a seed it cannot seal. |
| The QR on a printed form scans to nothing | `LEAVE_VERIFY_KEY` (and usually `LEAVE_PUBLIC_URL`) is not set. The number is still printed and still checkable by hand. |
| Nothing is authorized the way you expect | Someone changed the rules in `config/SecurityConfig.java`; they are verified by hand, not by the suite — see [`docs/TEST-PLAN.md`](docs/TEST-PLAN.md). |

## 9. Tests

```bash
cd backend && ./mvnw verify
```

611 unit tests in 57 classes, no database, no browser, no Spring context. They
pass on a laptop with no Oracle installed. What that means is not covered here
but in [`docs/TEST-PLAN.md`](docs/TEST-PLAN.md).

# Contributing

## Before you start

- Read `docs/ARCHITECTURE.md` and `docs/LEAVE-RULES.md`.
- Open an issue describing the change and the reason.
- Never commit secrets, personal employee data, database dumps or generated
  files.

## Branches and commits

- Branch from `main`: `feature/<short-name>`, `fix/<short-name>`,
  `docs/<short-name>`.
- Commit messages follow Conventional Commits:
  `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`.
- One logical change per commit; explain the reason in the body.
- Add your GitHub handle to `CHANGELOG.md` in the same pull request.

## Pull requests

- Describe what changed, why, and how it was tested.
- Link the issue.
- Keep the diff focused; do not mix formatting with behaviour changes.
- A reviewer must approve before merge; the author never merges their own
  security-relevant change without review.
- CI runs `./mvnw verify` on every push and pull request, and packages the
  production JAR in a second job (`.github/workflows/ci.yml`). The suite needs
  no database, so the pipeline proves the rules and the filters, not the
  migrations or the screens — see `docs/TEST-PLAN.md` for the manual pass.

## Code rules

- Java 21 language level (`<java.version>21</java.version>` in `backend/pom.xml`,
  required by Vaadin 25); standard Spring conventions; constructor injection.
- No business logic in controllers; services own transactions and rules.
- Every repository query that touches department data filters by scope.
- Validate input at the boundary and test the negative case.
- Add a database migration for every schema change; never edit an applied one.
- Log decisions, not personal data.

## Tests

- New behaviour needs a test; security rules need a positive and a negative
  test.
- Run the tests from `backend/`, where the Maven wrapper lives — there is no
  `mvnw` at the repository root. `JAVA_HOME` has to point at a JDK 21 first, or
  the wrapper builds against whatever Java is on the `PATH` and fails with
  "No compiler is provided in this environment" (see `INSTALL.md`):

  ```bash
  cd backend
  ./mvnw verify
  ```

- The suite is plain JUnit 5 with Mockito and AssertJ: repositories are mocked,
  and the servlet filters are exercised with Spring's `MockHttpServletRequest`,
  `MockHttpServletResponse` and `MockFilterChain`. No test starts a Spring
  context, opens a database or drives a browser, so nothing here needs a
  disposable database. Testcontainers, MockMvc, REST Assured and Vitest are not
  used.
- The consequence is worth stating plainly: the URL authorization rules in
  `config/SecurityConfig.java` have no automated test. They are verified by hand
  against a running server, and `docs/TEST-PLAN.md` records which checks are
  manual. A change to those rules needs that manual pass, not just a green
  `verify`.
- Anything that does need a real database — migrations, Oracle-specific SQL,
  transaction and locking behaviour — is checked by hand on a development
  schema, never on the live one.

## Documentation

- Update the affected document in `docs/` and the `CHANGELOG.md` entry.
- Record a decision in `docs/DECISIONS.md` when the change is architectural.
- Keep instructions runnable by a new developer on a clean machine.

## Review checklist

- [ ] Behaviour matches the requirement
- [ ] Department scope enforced and tested
- [ ] No secrets or personal data
- [ ] Migration included and reversible by restore
- [ ] Error handling produces no stack traces to clients
- [ ] Tests cover success and failure
- [ ] Docs and changelog updated

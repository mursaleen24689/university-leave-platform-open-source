<!--
One change per pull request, and say why before what. See CONTRIBUTING.md.
-->

**The problem this solves**

**How**

**Checklist**

- [ ] `cd backend && ./mvnw -B -ntp verify` passes
- [ ] New behaviour has a test that fails without it
- [ ] A rule change is written into `docs/LEAVE-RULES.md`, the screens and the
      tests, and nothing else
- [ ] A schema change is a **new** migration, never an edit to one that has run
- [ ] A new screen or API path states who may use it — and if `SecurityConfig`
      changed, the manual authorization table in `docs/TEST-PLAN.md` was walked
      through
- [ ] `CHANGELOG.md` has a line under Unreleased
- [ ] No secret, no real name, no real staff number, no database dump — including
      in test fixtures, screenshots and commit messages
- [ ] `git diff --stat` shows only files this change needs

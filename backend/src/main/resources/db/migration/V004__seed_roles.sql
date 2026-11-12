-- Fixed roles. Codes are referenced by the application and must not change.
-- A person may hold several roles; permissions are the union of the grants.

INSERT INTO app_role (code, name) VALUES ('EMPLOYEE', 'Employee');
INSERT INTO app_role (code, name) VALUES ('SUPERVISOR', 'Supervisor');
INSERT INTO app_role (code, name) VALUES ('HOD', 'Head of Department');
INSERT INTO app_role (code, name) VALUES ('PA_CLERK', 'PA / Clerk');
INSERT INTO app_role (code, name) VALUES ('DEPT_ADMIN', 'Department Administrator');
INSERT INTO app_role (code, name) VALUES ('CENTRAL_ADMIN', 'Central Administrator');
INSERT INTO app_role (code, name) VALUES ('AUDITOR', 'Auditor (read-only)');

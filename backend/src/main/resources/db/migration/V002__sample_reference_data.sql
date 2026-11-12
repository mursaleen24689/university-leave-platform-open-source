-- Clearly marked sample data for development only.
-- The official faculties, departments and offices are loaded from the HR
-- export in Phase 3. Do not treat these rows as the real university structure.
-- Single-row INSERTs are used deliberately: Oracle only gained multi-row VALUES
-- lists in 23ai, and this keeps the script valid on 19c and 21c too.

INSERT INTO faculty (code, name)
VALUES ('SAMPLE-ENG', 'Sample Faculty of Engineering');

INSERT INTO faculty (code, name)
VALUES ('SAMPLE-ADM', 'Sample Administrative Offices');

INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'SAMPLE-CSE', 'Sample Department of Computer Science', 'ACADEMIC'
FROM faculty
WHERE code = 'SAMPLE-ENG';

INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'SAMPLE-MTE', 'Sample Department of Engineering Sciences', 'ACADEMIC'
FROM faculty
WHERE code = 'SAMPLE-ENG';

INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'SAMPLE-HR', 'Sample Human Resources Office', 'ADMINISTRATIVE'
FROM faculty
WHERE code = 'SAMPLE-ADM';

INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'SAMPLE-FIN', 'Sample Finance Office', 'SUPPORT'
FROM faculty
WHERE code = 'SAMPLE-ADM';

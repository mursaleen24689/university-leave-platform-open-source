-- A starter organization: four faculties and the offices that hold the staff
-- who run the platform. Replace it with your own before the first employee is
-- imported - the administration screens add, rename and close these rows, so
-- nothing here is special except the department the bootstrap administrator
-- sits in. Codes are internal, permanent identifiers (see the table comments):
-- a wrong code is corrected by renaming the row, never by adding a second row.
--
-- V002 loaded SAMPLE- rows so the platform could be built before any real
-- structure existed. This file replaces them, and moves anyone already parked on
-- one to Central Administration rather than deleting their row from under them.
--
-- Single-row INSERTs are used for the same reason as in V002: the script stays
-- valid on Oracle versions that predate multi-row VALUES.

INSERT INTO faculty (code, name)
VALUES ('FENG', 'Faculty of Engineering and Technology');

INSERT INTO faculty (code, name)
VALUES ('FSCI', 'Faculty of Sciences and Humanities');

INSERT INTO faculty (code, name)
VALUES ('FMGT', 'Faculty of Management and Commerce');

INSERT INTO faculty (code, name)
VALUES ('AO', 'Administrative Offices');

-- Faculty of Engineering and Technology
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'CE', 'Department of Civil Engineering', 'ACADEMIC' FROM faculty WHERE code = 'FENG';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'EL', 'Department of Electrical Engineering', 'ACADEMIC' FROM faculty WHERE code = 'FENG';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'CO', 'Department of Computer Engineering', 'ACADEMIC' FROM faculty WHERE code = 'FENG';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'MT', 'Department of Mechatronics Engineering', 'ACADEMIC' FROM faculty WHERE code = 'FENG';

-- Faculty of Sciences and Humanities
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'MA', 'Department of Mathematics', 'ACADEMIC' FROM faculty WHERE code = 'FSCI';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'PH', 'Department of Physics', 'ACADEMIC' FROM faculty WHERE code = 'FSCI';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'CH', 'Department of Chemistry', 'ACADEMIC' FROM faculty WHERE code = 'FSCI';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'EN', 'Department of English Studies', 'ACADEMIC' FROM faculty WHERE code = 'FSCI';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'AR', 'Department of Architecture and Planning', 'ACADEMIC' FROM faculty WHERE code = 'FSCI';

-- Faculty of Management and Commerce
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'BA', 'Department of Business Administration', 'ACADEMIC' FROM faculty WHERE code = 'FMGT';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'AC', 'Department of Accounting and Finance', 'ACADEMIC' FROM faculty WHERE code = 'FMGT';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'EC', 'Department of Economics', 'ACADEMIC' FROM faculty WHERE code = 'FMGT';

-- The offices. Every employee needs a department, including the people whose job
-- is keeping the register, and the bootstrap administrator is one of them.
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'ADMIN', 'Central Administration', 'ADMINISTRATIVE' FROM faculty WHERE code = 'AO';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'HR', 'Human Resources', 'ADMINISTRATIVE' FROM faculty WHERE code = 'AO';
INSERT INTO department (faculty_id, code, name, department_type)
SELECT id, 'FN', 'Finance', 'ADMINISTRATIVE' FROM faculty WHERE code = 'AO';

-- Existing accounts sit on the sample rows; move them before the rows go.
UPDATE employee
   SET department_id = (SELECT id FROM department WHERE code = 'ADMIN')
 WHERE department_id IN (SELECT id FROM department WHERE code LIKE 'SAMPLE-%');

DELETE FROM department WHERE code LIKE 'SAMPLE-%';
DELETE FROM faculty WHERE code LIKE 'SAMPLE-%';

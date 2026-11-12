-- Staff records arrive in stages. Public sources list a name and designation,
-- while the staff number, e-mail and phone often come later from HR or never.
-- The platform must therefore accept a record with only the mandatory fields
-- and let an administrator fill the rest in as it arrives.
--
-- Oracle treats a unique constraint as satisfied by any number of NULLs, so
-- uk_employee_no and uk_employee_email keep protecting the values that exist.

ALTER TABLE employee MODIFY (employee_no NULL);
ALTER TABLE employee MODIFY (email NULL);

ALTER TABLE employee ADD (phone VARCHAR2(40 CHAR));

COMMENT ON COLUMN employee.employee_no IS 'University staff number; optional while HR data is incomplete';
COMMENT ON COLUMN employee.email IS 'Official e-mail; optional, filled in when known';
COMMENT ON COLUMN employee.phone IS 'Office or mobile number; optional, filled in when known';

CREATE INDEX idx_employee_name ON employee (full_name);

-- Every leave request now carries a number a person can quote.
--
-- The printable application form has to survive leaving the department: an
-- examiner with a sheet of paper in front of them needs something to read out
-- over the telephone and something the platform can look up. The row's id is a
-- number that means nothing outside this database, so the request gets a
-- reference of its own, in the same shape as a help-desk ticket, a record
-- correction and a delegated window - see docs/DECISIONS.md ADR-016.
--
-- The database writes it, not the application. ID is an identity column and
-- Oracle only gives it a value while the insert runs, so an INSERT statement
-- cannot name its own number and a second UPDATE to fill it in would have to
-- get past NOT NULL first. A BEFORE INSERT row trigger sees the id, because
-- Oracle assigns the identity value before that trigger fires, and it fires
-- whoever does the insert - the platform, a script or a database console - so
-- there is no way to end up with a request that has no number to quote.
--
-- Requests made before this migration are numbered from their ids here, so a
-- form printed for last month's leave carries a reference like one printed for
-- today's and the box is never empty.

ALTER TABLE leave_application ADD (reference VARCHAR2(20 CHAR));

UPDATE leave_application
   SET reference = 'LV-' || LPAD(TO_CHAR(id), 6, '0')
 WHERE reference IS NULL;

ALTER TABLE leave_application MODIFY (reference NOT NULL);

-- One index does two jobs: it makes the reference unique, so no two requests
-- can ever be quoted with the same number, and it is how the public
-- verification read finds the row.
ALTER TABLE leave_application ADD CONSTRAINT uk_leave_reference UNIQUE (reference);

COMMENT ON COLUMN leave_application.reference IS 'The number the applicant, the office and the printed form quote, for example LV-000123';

CREATE OR REPLACE TRIGGER leave_reference_bi
BEFORE INSERT ON leave_application
FOR EACH ROW
BEGIN
    :NEW.reference := 'LV-' || LPAD(TO_CHAR(:NEW.id), 6, '0');
END;
/

COMMENT ON COLUMN leave_application.reference IS 'Written by leave_reference_bi from the id, in the form LV-000123; never typed and never edited';

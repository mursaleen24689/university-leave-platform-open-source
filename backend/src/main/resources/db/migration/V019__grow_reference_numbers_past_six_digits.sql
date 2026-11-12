-- The quoted number grows past six digits instead of being cut down to them.
--
-- V014 to V017 all wrote the reference as LPAD(TO_CHAR(id), 6, '0'). LPAD does
-- not only pad: when the text is already longer than the asked-for length it
-- keeps the LEFTMOST characters and throws the rest away. So id 1000000 was
-- written as '100000' - the very same reference id 100000 already holds. The
-- unique index on the column then refuses the insert, which means the platform
-- would stop accepting new leave requests, tickets, record corrections and
-- delegated windows at the millionth row, with an ORA-00001 that says nothing
-- about the real cause.
--
-- The application never had this fault: LeaveApplication#stampReference and its
-- three counterparts use String.format("%06d", id), where 6 is a MINIMUM width.
-- Past a million the screen would have quoted LV-1000000 while the column held
-- LV-100000, so the printed form and the database disagreed about the number.
-- This makes the trigger mean what the Java already means.
--
-- GREATEST(6, LENGTH(...)) is that minimum width: pad to six while the number is
-- shorter, and to its own length once it is longer, which leaves it untouched.
-- Every reference already stored was written from an id below a million, so none
-- of them changes and no printed form, QR picture or verification code already
-- in somebody's hands is invalidated. Nothing is updated here for that reason.
--
-- The column stays VARCHAR2(20 CHAR): three characters of prefix and seventeen
-- digits, which is a hundred thousand times more rows than the identity column
-- of a university leave platform will ever hold.

CREATE OR REPLACE TRIGGER leave_reference_bi
BEFORE INSERT ON leave_application
FOR EACH ROW
BEGIN
    :NEW.reference := 'LV-' || LPAD(TO_CHAR(:NEW.id), GREATEST(6, LENGTH(TO_CHAR(:NEW.id))), '0');
END;
/

CREATE OR REPLACE TRIGGER ticket_reference_bi
BEFORE INSERT ON support_ticket
FOR EACH ROW
BEGIN
    :NEW.reference := 'HD-' || LPAD(TO_CHAR(:NEW.id), GREATEST(6, LENGTH(TO_CHAR(:NEW.id))), '0');
END;
/

CREATE OR REPLACE TRIGGER profile_reference_bi
BEFORE INSERT ON profile_request
FOR EACH ROW
BEGIN
    :NEW.reference := 'PR-' || LPAD(TO_CHAR(:NEW.id), GREATEST(6, LENGTH(TO_CHAR(:NEW.id))), '0');
END;
/

CREATE OR REPLACE TRIGGER delegation_reference_bi
BEFORE INSERT ON approval_delegation
FOR EACH ROW
BEGIN
    :NEW.reference := 'DL-' || LPAD(TO_CHAR(:NEW.id), GREATEST(6, LENGTH(TO_CHAR(:NEW.id))), '0');
END;
/
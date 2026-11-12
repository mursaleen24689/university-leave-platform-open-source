-- The ticket number is written by the database, not by the application.
--
-- SUPPORT_TICKET.REFERENCE is derived from ID, and ID only exists at the
-- moment the row is inserted. An application that saves the row and then fills
-- the number in a second statement cannot get past the NOT NULL constraint -
-- the first statement is the insert, and it has to carry something. So the
-- number is stamped inside the insert itself, by a BEFORE INSERT row trigger.
--
-- Oracle gives an IDENTITY column its value before the BEFORE INSERT row
-- trigger runs, which is what makes this work. The trigger fires whether the
-- row arrives from the platform, from a script, or from a database console, so
-- there is no way to end up with a ticket that has no number to quote.
--
-- The application keeps the same rule in com.example.leaveplatform.support.SupportTicket
-- #stampReference so the number can be shown to the person who just typed it
-- without reading the row back. That copy is only for display: the trigger is
-- what writes the column, and the mapping marks the column read-only, so a
-- drift between the two would show a wrong number for one second and never
-- store a wrong one.

CREATE OR REPLACE TRIGGER ticket_reference_bi
BEFORE INSERT ON support_ticket
FOR EACH ROW
BEGIN
    :NEW.reference := 'HD-' || LPAD(TO_CHAR(:NEW.id), 6, '0');
END;
/

COMMENT ON COLUMN support_ticket.reference IS 'Written by ticket_reference_bi from the id, in the form HD-000042; never typed and never edited';

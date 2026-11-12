-- Two decisions the live the earlier application application supports and this platform did not:
-- the applicant cancelling a request that has not been decided, and approving
-- a request as compensatory leave without spending balance days.
--
-- the earlier application deletes a pending request on withdraw. Here the row is kept and moved to
-- WITHDRAWN instead, because the audit trail and the leave register both need
-- to show a request that existed and was cancelled.

ALTER TABLE leave_application DROP CONSTRAINT ck_leave_status;

ALTER TABLE leave_application ADD CONSTRAINT ck_leave_status CHECK (status IN
    ('PENDING_SUPERVISOR', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'));

ALTER TABLE leave_application ADD (
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    compensated  NUMBER(1) DEFAULT 0 NOT NULL
);

ALTER TABLE leave_application ADD CONSTRAINT ck_leave_compensated CHECK (compensated IN (0, 1));

COMMENT ON COLUMN leave_application.withdrawn_at IS
    'When the applicant withdrew the request; only set on status WITHDRAWN';
COMMENT ON COLUMN leave_application.compensated IS
    'Approved as compensatory leave, so no days were deducted from the balance';

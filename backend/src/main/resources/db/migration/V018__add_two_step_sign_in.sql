-- Two-step sign-in with an authenticator app (docs/SECURITY.md,
-- "Authentication"; docs/DECISIONS.md ADR-021 for why the code comes first).
--
-- A password alone is what a phishing page, a reused login on another site or
-- a shoulder-surfed keyboard buys an attacker. An authenticator app adds a
-- second thing to have: a six-digit number that only the person's own phone
-- produces, changes every thirty seconds and never travels over the network
-- except as the number itself. The platform stores no copy of the phone and no
-- secret question - only the shared seed that lets it work out the same number
-- the app is showing.
--
-- WHY IT IS OPTIONAL. The university has just opened this platform to
-- thirty-two departments; the people who sign in daily include clerks who share
-- an office computer and heads of department who will not install an app for a
-- system they use twice a month. Making it a condition of signing in would
-- push those people back to the paper register, which is the failure this
-- platform exists to end. So the second step is per account, off until the
-- person turns it on, and the sign-in screen explains the box rather than
-- demanding it.
--
-- WHY THE SEED IS ENCRYPTED AND NOT HASHED. A password is compared, so keeping
-- only a one-way hash is enough - that is what password_hash does. This seed is
-- the other way round: the platform must read it back every time it works out
-- the expected number, so it has to be reversible. It is therefore sealed with
-- AES-GCM under a key the database never holds (LEAVE_TWOSTEP_KEY), which means a
-- stolen dump yields ciphertext and a stolen database backup is not a bag of
-- second factors. GCM rather than CBC because it authenticates: a hand-edited
-- or truncated value fails to open instead of returning characters that look
-- like a seed and are not one.
--
-- WHY A SEPARATE KEY FROM LEAVE_MAIL_KEY. Both are sealed with the same kind of
-- cipher, and that is where the similarity ends. The mail seed unlocks the
-- university's outgoing mail credentials; this one unlocks the second factors
-- of every account. One phrase covering both means one leaked file is two
-- breaches, so each secret class keeps its own key and its own failure: losing
-- LEAVE_TWOSTEP_KEY costs enrolled people a re-enrolment, and says nothing at all
-- about mail. The mechanics are duplicated in auth.EnrolmentCipher rather than
-- shared with mail.SecretCipher for the same reason - the two must be able to
-- be rotated, replaced and audited apart.
--
-- WHY A TIMESTAMP ALONGSIDE THE SEED. "On since when" is the question an
-- office asks first when somebody's phone is missing, and it is the difference
-- between "this account was protected last month" and "nobody has ever turned
-- this on". It is written and cleared with the seed and by nothing else, so the
-- two can never disagree about whether two-step is on.
--
-- There is no column for "required": whether the second step happens is read
-- from the presence of a seed, so an account cannot be enrolled with nothing to
-- check against, or carry a seed nobody is being asked to use.

ALTER TABLE app_user ADD (
    two_factor_secret       VARCHAR2(200 CHAR) NULL,
    two_factor_enrolled_at  TIMESTAMP WITH TIME ZONE NULL);

-- A seed is either absent or one this platform sealed. Without the constraint a
-- row typed by hand in a database console would look enrolled and then fail on
-- every sign-in attempt, which is the worst moment to discover it.
ALTER TABLE app_user ADD (
    CONSTRAINT ck_app_user_two_factor_secret CHECK
        (two_factor_secret IS NULL OR two_factor_secret LIKE 'v1:%'),
    CONSTRAINT ck_app_user_two_factor_pair CHECK
        ((two_factor_secret IS NULL AND two_factor_enrolled_at IS NULL)
         OR (two_factor_secret IS NOT NULL AND two_factor_enrolled_at IS NOT NULL)));

COMMENT ON TABLE app_user IS 'Sign-in account: password, lock state and the optional authenticator seed';
COMMENT ON COLUMN app_user.two_factor_secret IS 'The shared seed, sealed with AES-GCM under LEAVE_TWOSTEP_KEY and prefixed v1:; NULL means this account signs in with its password alone';
COMMENT ON COLUMN app_user.two_factor_enrolled_at IS 'When the person confirmed the seed with a code from their app; written and cleared with the seed';

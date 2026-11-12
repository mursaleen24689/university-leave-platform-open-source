package com.example.leaveplatform.identity;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The address a person can be mailed at, looked up from the sign-in name a
 * notification was written for.
 *
 * <p>Kept in the identity module because the answer depends on three things that
 * live here: whether the account still exists, whether it is still enabled, and
 * whether the staff record it points at still carries an address. A mail module
 * that asked each of those separately would duplicate the rules about when a
 * person counts as reachable.
 *
 * <p>Nothing is invented. An employee with a blank address is not mailed and the
 * reason is reported by the caller, because guessing an address from a username
 * sends university leave news to a stranger's inbox.
 */
@Service
public class ContactLookup {

    private final AppUserRepository accounts;

    public ContactLookup(AppUserRepository accounts) {
        this.accounts = accounts;
    }

    /** The address saved for a sign-in name, or the fact that there is not one. */
    public record Contact(String username, String address) {

        public boolean reachable() {
            return address != null && !address.isBlank();
        }
    }

    /** Empty when the account is gone or disabled: a closed account is not mailed. */
    @Transactional(readOnly = true)
    public Optional<Contact> find(String username) {
        return accounts.findByUsername(username).filter(AppUser::isEnabled).map(account -> {
            Employee person = account.getEmployee();
            boolean serving = person != null && person.isActive();
            return new Contact(username, serving ? person.getEmail() : null);
        });
    }
}

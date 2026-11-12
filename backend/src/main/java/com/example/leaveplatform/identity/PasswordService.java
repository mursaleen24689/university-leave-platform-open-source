package com.example.leaveplatform.identity;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;

/**
 * Self-service password change. The current password is always required, the
 * new one must satisfy {@link PasswordPolicy} and must not match the current
 * password or any of the last five stored hashes.
 */
@Service
public class PasswordService {

    static final int HISTORY_DEPTH = 5;

    private final AppUserRepository users;
    private final PasswordHistoryRepository history;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy policy;
    private final AuditService audit;

    public PasswordService(AppUserRepository users, PasswordHistoryRepository history,
            PasswordEncoder passwordEncoder, PasswordPolicy policy, AuditService audit) {
        this.users = users;
        this.history = history;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional
    public void change(String username, String currentPassword, String newPassword) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        policy.check(newPassword, user.getUsername());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new WeakPasswordException("New password must differ from the current password.");
        }
        List<PasswordHistory> recent = history.findByUserIdOrderByChangedAtDesc(user.getId());
        boolean reused = recent.stream()
                .limit(HISTORY_DEPTH)
                .anyMatch(entry -> passwordEncoder.matches(newPassword, entry.getPasswordHash()));
        if (reused) {
            throw new WeakPasswordException("That password was used recently. Choose another one.");
        }

        history.save(new PasswordHistory(user.getId(), user.getPasswordHash()));
        user.changePassword(passwordEncoder.encode(newPassword));
        trim(user.getId());
        audit.recordAs(username, AuditAction.PASSWORD_CHANGED, "ACCOUNT", user.getId(),
                "Changed their own password.");
    }

    private void trim(Long userId) {
        List<PasswordHistory> entries = history.findByUserIdOrderByChangedAtDesc(userId);
        entries.stream().skip(HISTORY_DEPTH).forEach(history::delete);
    }
}

package com.example.leaveplatform.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * The authenticator seed, sealed. It is reversible by design - the platform
     * has to read it back to work out the number to expect - so it is kept as
     * ciphertext under a key the database does not hold. Nothing outside
     * {@code auth.TwoStepService} opens it and no read path returns it.
     */
    @Column(name = "two_factor_secret", length = 200)
    private String twoFactorSecret;

    @Column(name = "two_factor_enrolled_at")
    private Instant twoFactorEnrolledAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<AppRole> roles = new HashSet<>();

    protected AppUser() {
    }

    AppUser(Employee employee, String username, String passwordHash, boolean mustChangePassword) {
        this.employee = employee;
        this.username = username;
        this.passwordHash = passwordHash;
        this.mustChangePassword = mustChangePassword;
        this.enabled = true;
        this.failedAttempts = 0;
    }

    /**
     * Creates an account that must choose its own password at the first sign-in.
     * Used by the administration screen when an account is opened formally.
     */
    public AppUser(Employee employee, String username, String passwordHash) {
        this(employee, username, passwordHash, true);
    }

    void addRole(AppRole role) {
        roles.add(role);
    }

    /** Replaces the whole permission set; used when an administrator edits roles. */
    public void replaceRoles(Collection<AppRole> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    /**
     * Sets a new temporary password, forces a change at the next sign-in and
     * clears any lock left by failed attempts.
     */
    public void resetPassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = true;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
    }

    /**
     * Turns the second step on. Both halves move together: a seed with no
     * confirmation timestamp would leave the database check saying something the
     * constraint forbids, so they are written and cleared by this pair of
     * methods and by nothing else.
     */
    public void enrolTwoStep(String sealedSeed, Instant confirmedAt) {
        this.twoFactorSecret = sealedSeed;
        this.twoFactorEnrolledAt = confirmedAt;
    }

    /**
     * Turns the second step off, for the person themselves or for the
     * administration team when a phone is gone. There is no history kept: an
     * old seed that nobody can use any more is a liability, not a record, and
     * the audit trail already says who did it and why.
     */
    public void clearTwoStep() {
        this.twoFactorSecret = null;
        this.twoFactorEnrolledAt = null;
    }

    /** Whether this account must also produce a number from an app to sign in. */
    public boolean hasTwoStep() {
        return twoFactorSecret != null && !twoFactorSecret.isBlank();
    }

    /**
     * Counts a failed sign-in and locks the account when the limit is reached.
     * Failures while the account is already locked are ignored, so repeated
     * attempts cannot extend a lock indefinitely.
     *
     * <p>A wrong sign-in code is counted here as well as a wrong password, on
     * purpose: one limit then caps guessing at either half of what an enrolled
     * account needs, instead of leaving a six-digit number as the only thing
     * standing between an attacker and an unlocked password.
     */
    public void registerFailedAttempt(int maxFailures, Duration lockDuration) {
        Instant now = Instant.now();
        if (isLocked(now)) {
            return;
        }
        failedAttempts++;
        if (failedAttempts >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedAttempts = 0;
        }
    }

    void registerSuccessfulLogin() {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = Instant.now();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    /**
     * The sealed seed, for {@code auth.TwoStepService} and for nothing else. It
     * is not a display value: no response, report or screen ever carries it,
     * sealed or otherwise.
     */
    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public Instant getTwoFactorEnrolledAt() {
        return twoFactorEnrolledAt;
    }

    public Set<AppRole> getRoles() {
        return roles;
    }
}

package com.example.leaveplatform.identity;

import java.time.Instant;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads login accounts from the database. Role codes become authorities with a
 * {@code ROLE_} prefix, so {@code HOD} is reachable as {@code hasRole("HOD")}.
 *
 * The {@code UsernameNotFoundException} below stays cheap on purpose, even though
 * a refusal that returns without touching a password hash would otherwise leak
 * which usernames exist. {@code DaoAuthenticationProvider} catches this exception
 * and compares the typed password against a hash of its own before it reports the
 * failure, so an unknown username already costs what a wrong password costs. A
 * second comparison added here makes the unknown username the slower of the two,
 * which leaks the same list the other way round.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No account with that username"));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(AppRole::getCode)
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + code))
                .toList();

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled() || !user.getEmployee().isActive())
                .accountLocked(user.isLocked(Instant.now()))
                .build();
    }
}

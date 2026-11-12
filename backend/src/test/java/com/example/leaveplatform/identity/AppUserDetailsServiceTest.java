package com.example.leaveplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AppUserDetailsServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppUserDetailsService service = new AppUserDetailsService(users);

    @Test
    void rejectsAnUnknownUsername() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void mapsRoleCodesToPrefixedAuthorities() {
        AppUser user = user("z.ahmed", true, true, false);
        when(users.findByUsername("z.ahmed")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("z.ahmed");

        assertThat(details.getUsername()).isEqualTo("z.ahmed");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_CENTRAL_ADMIN", "ROLE_EMPLOYEE");
    }

    @Test
    void disablesTheAccountWhenTheUserIsDisabled() {
        AppUser user = user("z.ahmed", false, true, false);
        when(users.findByUsername("z.ahmed")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("z.ahmed").isEnabled()).isFalse();
    }

    @Test
    void disablesTheAccountWhenTheEmployeeLeftTheUniversity() {
        AppUser user = user("z.ahmed", true, false, false);
        when(users.findByUsername("z.ahmed")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("z.ahmed").isEnabled()).isFalse();
    }

    @Test
    void locksTheAccountUntilTheLockExpires() {
        AppUser user = user("z.ahmed", true, true, true);
        when(users.findByUsername("z.ahmed")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("z.ahmed");

        assertThat(details.isAccountNonLocked()).isFalse();
        assertThat(details.isEnabled()).isTrue();
    }

    private AppUser user(String username, boolean enabled, boolean employeeActive, boolean locked) {
        AppRole centralAdmin = mock(AppRole.class);
        when(centralAdmin.getCode()).thenReturn("CENTRAL_ADMIN");
        AppRole employeeRole = mock(AppRole.class);
        when(employeeRole.getCode()).thenReturn("EMPLOYEE");

        Employee employee = mock(Employee.class);
        when(employee.isActive()).thenReturn(employeeActive);

        Set<AppRole> roles = Set.of(centralAdmin, employeeRole);

        AppUser user = mock(AppUser.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getPasswordHash()).thenReturn("$2a$10$hash");
        when(user.isEnabled()).thenReturn(enabled);
        when(user.getEmployee()).thenReturn(employee);
        when(user.getRoles()).thenReturn(roles);
        when(user.isLocked(any(Instant.class))).thenReturn(locked);
        return user;
    }
}

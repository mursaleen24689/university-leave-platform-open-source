package com.example.leaveplatform.auth;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;

@Service
public class AuthQueryService {

    private final AppUserRepository users;

    public AuthQueryService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public MeResponse profile(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Employee employee = user.getEmployee();
        List<String> roles = user.getRoles().stream()
                .map(AppRole::getCode)
                .sorted()
                .toList();
        return new MeResponse(
                user.getUsername(),
                roles,
                employee.getEmployeeNo(),
                employee.getFullName(),
                employee.getDesignation(),
                employee.getDepartment().getCode(),
                employee.getDepartment().getName(),
                user.isMustChangePassword());
    }
}

package com.example.leaveplatform.audit;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only JSON view of the audit trail. The rule in {@code SecurityConfig}
 * allows only a central administrator and an auditor; an auditor holds no other
 * role that can write, which is what makes the role read-only.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/events")
    List<AuditEntry> events(@RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return audit.recent(q, from, to);
    }
}

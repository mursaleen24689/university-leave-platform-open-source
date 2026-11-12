package com.example.leaveplatform.leave;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON endpoints for leave. The Vaadin screens call {@link LeaveService}
 * directly; these endpoints serve API clients and are the quickest way to
 * check the rules from a script.
 */
@RestController
@RequestMapping("/api/v1/leave")
public class LeaveController {

    private final LeaveService leave;

    public LeaveController(LeaveService leave) {
        this.leave = leave;
    }

    @GetMapping("/balance")
    LeaveBalanceResponse balance(Authentication authentication) {
        return leave.balance(authentication.getName());
    }

    @GetMapping("/applications")
    List<LeaveApplicationResponse> myApplications(Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) LeaveStatus status) {
        return leave.myApplications(authentication.getName(), new LeaveFilter(from, to, status));
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    LeaveApplicationResponse submit(Authentication authentication,
            @Valid @RequestBody SubmitLeaveRequest request) {
        return leave.submit(authentication.getName(), request);
    }

    /**
     * Both queues only ever hold waiting requests, so the period is the filter
     * worth offering here; a status would either empty a queue or change nothing.
     */
    @GetMapping("/approvals")
    ApprovalQueueResponse approvals(Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return leave.approvals(authentication.getName(), new LeaveFilter(from, to, null));
    }

    @PostMapping("/applications/{id}/supervisor")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supervisorDecision(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody SupervisorDecisionRequest request) {
        leave.supervisorDecision(authentication.getName(), id, request.recommend(), request.remarks());
    }

    @PostMapping("/applications/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decide(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody LeaveDecisionRequest request) {
        leave.decide(authentication.getName(), id, request.approve(), request.compensate(),
                request.remarks());
    }

    @PostMapping("/applications/{id}/withdraw")
    LeaveApplicationResponse withdraw(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody WithdrawRequest request) {
        return leave.withdraw(authentication.getName(), id, request.remarks());
    }
}

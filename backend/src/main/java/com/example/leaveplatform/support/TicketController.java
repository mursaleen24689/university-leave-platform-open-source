package com.example.leaveplatform.support;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON endpoints for the staff side of the help desk. The Vaadin screen calls
 * {@link TicketService} directly; these exist for API clients and for checking
 * the rules from a script, which is how the live verification is run.
 *
 * <p>Every method takes the person from the session and never from the body, so
 * a caller cannot ask for somebody else's tickets or write a line as somebody
 * else. The desk's own endpoints are under {@code /api/v1/admin/tickets}, which
 * is where {@code config/SecurityConfig} already insists on the administrator
 * role.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping
    List<TicketResponse> mine(Authentication authentication) {
        return tickets.mine(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TicketResponse submit(Authentication authentication,
            @Valid @RequestBody SubmitTicketRequest request) {
        return tickets.submit(authentication.getName(), request);
    }

    @GetMapping("/{id}")
    TicketThreadResponse thread(Authentication authentication, @PathVariable Long id) {
        return tickets.thread(authentication.getName(), id);
    }

    @PostMapping("/{id}/reply")
    TicketResponse reply(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody TicketReplyRequest request) {
        return tickets.replyByStaff(authentication.getName(), id, request.body());
    }

    @PostMapping("/{id}/reopen")
    TicketResponse reopen(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody TicketReplyRequest request) {
        return tickets.reopen(authentication.getName(), id, request.body());
    }
}

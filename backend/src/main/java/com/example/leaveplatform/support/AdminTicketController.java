package com.example.leaveplatform.support;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The desk's side of the help desk, under {@code /api/v1/admin/**} where
 * {@code config/SecurityConfig} already requires the central administrator role.
 *
 * <p>Nothing here decides whether the caller may act - {@link TicketService}
 * checks the role again, because the screen path does not come through these
 * endpoints at all and one rule has to hold for both.
 */
@RestController
@RequestMapping("/api/v1/admin/tickets")
class AdminTicketController {

    private final TicketService tickets;

    AdminTicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    /**
     * The queue, oldest first. Adding {@code ?all=true} gives every ticket in
     * stead, which is what the screen asks for when somebody wants to see what
     * has already been closed.
     */
    @GetMapping
    List<TicketResponse> queue(@RequestParam(defaultValue = "true") boolean waitingOnly) {
        return waitingOnly ? tickets.queue() : tickets.all();
    }

    @GetMapping("/desk-members")
    List<DeskMemberResponse> deskMembers() {
        return tickets.deskMembers();
    }

    @GetMapping("/{id}")
    TicketThreadResponse thread(Authentication authentication, @PathVariable Long id) {
        return tickets.thread(authentication.getName(), id);
    }

    @PostMapping("/{id}/reply")
    TicketResponse reply(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody TicketReplyRequest request) {
        return tickets.replyByDesk(authentication.getName(), id, request.body());
    }

    @PostMapping("/{id}/assign")
    TicketResponse assign(Authentication authentication, @PathVariable Long id,
            @RequestBody TicketAssignRequest request) {
        return tickets.assign(authentication.getName(), id, request.username());
    }

    @PostMapping("/{id}/close")
    TicketResponse close(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody TicketReplyRequest request) {
        return tickets.close(authentication.getName(), id, request.body());
    }
}

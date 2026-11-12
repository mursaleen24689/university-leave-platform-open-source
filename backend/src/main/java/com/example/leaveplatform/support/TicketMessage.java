package com.example.leaveplatform.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import com.example.leaveplatform.identity.Employee;

/**
 * One line of a ticket conversation, written once and never changed. Both sides
 * read the same list; {@code fromDesk} says whose words they are, and it is set
 * from the writer's role in the service rather than accepted from a caller,
 * because a staff member who could claim to be the desk would own the thread.
 */
@Entity
@Table(name = "ticket_message")
public class TicketMessage {

    public static final int MAX_BODY = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_employee_id", nullable = false)
    private Employee author;

    @Column(name = "from_desk", nullable = false, updatable = false)
    private Boolean fromDesk;

    @Column(nullable = false, updatable = false, length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketMessage() {
    }

    public TicketMessage(SupportTicket ticket, Employee author, boolean fromDesk, String body) {
        this.ticket = ticket;
        this.author = author;
        this.fromDesk = fromDesk;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SupportTicket getTicket() {
        return ticket;
    }

    public Employee getAuthor() {
        return author;
    }

    public boolean isFromDesk() {
        return Boolean.TRUE.equals(fromDesk);
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

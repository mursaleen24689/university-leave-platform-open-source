package com.example.leaveplatform.support;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    /** Oldest first: a conversation is read in the order it happened. */
    @Query("""
            select m from TicketMessage m
            where m.ticket.id = :ticketId
            order by m.createdAt asc, m.id asc
            """)
    List<TicketMessage> findThread(@Param("ticketId") Long ticketId);
}

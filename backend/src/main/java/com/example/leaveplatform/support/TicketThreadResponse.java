package com.example.leaveplatform.support;

import java.util.List;

/** A ticket together with the whole conversation, oldest line first. */
public record TicketThreadResponse(TicketResponse ticket, List<TicketMessageResponse> messages) {
}

package com.example.leaveplatform.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * The footer every screen and the sign-in page carry. It names the platform and
 * the office it belongs to, and nothing about the person who built it - a tool
 * this size outlives the arrangement of one desk, and a copyright line that names
 * an employee is the wrong owner of the record.
 */
class CreditFooter extends Div {

    CreditFooter() {
        Span prepared = new Span("University Leave Platform");
        Span role = new Span("Leave, attendance and approvals for every wing of the institution");
        Span copyright = new Span("Copyright © the institution, 2026");
        prepared.getElement().getStyle().set("font-weight", "600");

        addClassName("app-footer");
        add(prepared, new Div(role), new Div(copyright));
    }
}

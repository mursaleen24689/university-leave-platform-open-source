package com.example.leaveplatform.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * A row of fields and buttons that gives up and wraps instead of pushing the
 * page sideways. Every form row on every screen is built this way: a phone is
 * narrower than three text fields and a button, and the only other choices are
 * a horizontal scrollbar or a button that cannot be reached.
 *
 * <p>The {@code app-fields} class in app.css lines the parts up along their
 * bottom edge on a wide screen and stacks them one per line on a narrow one.
 * That alignment is left to the stylesheet on purpose: an inline setting from
 * Java would beat the media query and the phone would keep the staggered look.
 */
final class Rows {

    private Rows() {
    }

    static HorizontalLayout wrap(Component... parts) {
        HorizontalLayout row = new HorizontalLayout(parts);
        row.addClassName("app-fields");
        row.setWrap(true);
        return row;
    }
}

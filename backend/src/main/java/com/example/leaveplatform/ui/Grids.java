package com.example.leaveplatform.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;

/**
 * The two things every grid on every screen needs and a view used to each invent
 * its own way of doing: a height that stops at a limit so a long list scrolls
 * inside the grid instead of pushing the rest of the page down, and a sentence
 * saying why the grid is empty.
 */
final class Grids {

    /** About ten rows; a short list stays short, a long one scrolls. */
    private static final String DEFAULT_MAX_HEIGHT = "22rem";

    private Grids() {
    }

    static void scrollable(Grid<?> grid) {
        grid.setMaxHeight(DEFAULT_MAX_HEIGHT);
    }

    static void scrollable(Grid<?> grid, String maxHeight) {
        grid.setMaxHeight(maxHeight);
    }

    static void emptyText(Grid<?> grid, String text) {
        Paragraph none = new Paragraph(text);
        none.addClassName("app-empty");
        grid.setEmptyStateComponent(none);
    }
}

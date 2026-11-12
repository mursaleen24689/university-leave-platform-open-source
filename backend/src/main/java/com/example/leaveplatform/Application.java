package com.example.leaveplatform;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.aura.Aura;

/**
 * Vaadin 25 app shell. The theme must be named explicitly; without this class
 * the framework guesses Aura and serves the stylesheet from an unmapped path.
 * {@code app.css} carries the university colours, the header, the cards and the
 * credit footer; see {@code docs/ARCHITECTURE.md}.
 */
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("app.css")
public class Application implements AppShellConfigurator {
}

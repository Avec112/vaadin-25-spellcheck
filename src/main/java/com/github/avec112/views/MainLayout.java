package com.github.avec112.views;

import com.github.avec112.vocabulary.VocabularyView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

/** Application frame with navigation and the logged-in user. */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authentication) {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Spell check demo", SpellCheckDemoView.class),
                new SideNavItem("Dictionary", VocabularyView.class));
        addToDrawer(nav);

        String user = authentication.getPrincipalName().orElse("");
        Button logout = new Button("Log out", e -> authentication.logout());
        logout.addThemeVariants(ButtonVariant.TERTIARY);
        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), new Span("Spell check"), new Span(user),
                logout);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(header.getComponentAt(1));
        header.getStyle().set("padding-right", "var(--lumo-space-m)");
        addToNavbar(header);
    }
}

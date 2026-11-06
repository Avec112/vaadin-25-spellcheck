package com.github.avec112.vocabulary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.github.avec112.spellcheck.SpellCheckLanguage;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;

import jakarta.annotation.security.PermitAll;

/**
 * Lists the shared spell-check vocabulary. Every user can add, edit and
 * delete words; the change log below the list shows who changed what, when
 * and why. The page updates itself when another user changes the vocabulary.
 */
@Route("dictionary")
@PageTitle("Dictionary")
@PermitAll
public class VocabularyView extends VerticalLayout {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final VocabularyService service;
    private final TextField filter = new TextField();
    private final Grid<VocabularyWord> words = new Grid<>();
    private final Grid<VocabularyWordChange> history = new Grid<>();
    private final H3 historyTitle = new H3();
    private Registration changeRegistration;

    public VocabularyView(VocabularyService service) {
        this.service = service;
        setSizeFull();
        setMaxWidth("72rem");

        add(new H2("Dictionary"), new Paragraph("Words accepted by the spell checker for all users, for example "
                + "product names and abbreviations. Changes take effect immediately for everybody."));

        filter.setPlaceholder("Search");
        filter.setClearButtonVisible(true);
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.addValueChangeListener(e -> refresh());
        Button add = new Button("Add word", e -> WordDialog.openAdd(service, this::refresh));
        add.addThemeVariants(ButtonVariant.PRIMARY);
        HorizontalLayout toolbar = new HorizontalLayout(filter, add);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        add(toolbar);

        configureWords();
        configureHistory();
        add(words, historyTitle, history);
        setFlexGrow(2, words);
        setFlexGrow(1, history);
        refresh();
    }

    private void configureWords() {
        words.addColumn(VocabularyWord::getWord).setHeader("Word").setSortable(true).setFlexGrow(2);
        words.addColumn(w -> WordDialog.scopeLabel(w.getLanguage().orElse(null))).setHeader("Applies to")
                .setSortable(true);
        words.addColumn(VocabularyWord::getCreatedBy).setHeader("Added by").setSortable(true);
        words.addColumn(w -> format(w.getCreatedAt())).setHeader("Added").setSortable(true);
        words.addColumn(w -> w.getUpdatedAt().equals(w.getCreatedAt()) ? ""
                : w.getUpdatedBy() + ", " + format(w.getUpdatedAt())).setHeader("Last changed");
        words.addComponentColumn(w -> {
            Button edit = new Button("Edit", e -> WordDialog.openEdit(service, w, this::refresh));
            Button delete = new Button("Delete", e -> WordDialog.openDelete(service, w, this::refresh));
            edit.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            delete.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL, ButtonVariant.ERROR);
            edit.setAriaLabel("Edit " + w.getWord());
            delete.setAriaLabel("Delete " + w.getWord());
            return new HorizontalLayout(edit, delete);
        }).setAutoWidth(true).setFlexGrow(0);
        words.addThemeVariants(GridVariant.ROW_STRIPES);
        words.setEmptyStateText("No words yet. Words added from a spell-checked field appear here.");
        words.asSingleSelect().addValueChangeListener(e -> refreshHistory());
    }

    private void configureHistory() {
        history.addColumn(c -> format(c.getChangedAt())).setHeader("When").setAutoWidth(true).setFlexGrow(0);
        history.addColumn(VocabularyWordChange::getChangedBy).setHeader("User").setAutoWidth(true).setFlexGrow(0);
        history.addColumn(VocabularyView::describe).setHeader("Change").setFlexGrow(2);
        history.addColumn(c -> Optional.ofNullable(c.getComment()).orElse("")).setHeader("Comment").setFlexGrow(2);
        history.addThemeVariants(GridVariant.WRAP_CELL_CONTENT);
        history.setEmptyStateText("No changes yet.");
    }

    @Override
    protected void onAttach(AttachEvent event) {
        UI ui = event.getUI();
        // Another user changed the vocabulary: show it without a reload.
        changeRegistration = service.addChangeListener(() -> ui.access(this::refresh));
    }

    @Override
    protected void onDetach(DetachEvent event) {
        changeRegistration.remove();
    }

    private void refresh() {
        VocabularyWord selected = words.asSingleSelect().getValue();
        words.setItems(service.findWords(filter.getValue()));
        // Keep the selection if the word still exists.
        if (selected != null) {
            words.getListDataView().getItems().filter(w -> w.getId().equals(selected.getId())).findFirst()
                    .ifPresentOrElse(words::select, words::deselectAll);
        }
        refreshHistory();
    }

    private void refreshHistory() {
        VocabularyWord selected = words.asSingleSelect().getValue();
        if (selected == null) {
            historyTitle.setText("Recent changes");
            history.setItems(service.recentChanges());
        } else {
            historyTitle.setText("Changes of \"" + selected.getWord() + "\"");
            history.setItems(service.changesOf(selected.getId()));
        }
    }

    private static String describe(VocabularyWordChange change) {
        String now = change.getWord() + " (" + scope(change.getLanguageScope()) + ")";
        return switch (change.getAction()) {
            case ADDED -> "Added " + now;
            case DELETED -> "Deleted " + now;
            case UPDATED -> "Changed " + change.getPreviousWord() + " (" + scope(change.getPreviousLanguageScope())
                    + ") to " + now;
        };
    }

    private static String scope(String languageScope) {
        return WordDialog.scopeLabel(SpellCheckLanguage.fromCode(languageScope).orElse(null));
    }

    private static String format(Instant instant) {
        return TIME.format(instant);
    }
}

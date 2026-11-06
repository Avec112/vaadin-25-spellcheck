package com.github.avec112.vocabulary;

import com.github.avec112.spellcheck.SpellCheckLanguage;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Add, edit or delete a dictionary word, with an optional comment explaining
 * the change. The comment is stored in the change log.
 */
final class WordDialog extends Dialog {

    private final VocabularyService service;
    private final VocabularyWord existing;
    private final Runnable onSaved;
    private final boolean deleteMode;
    private final TextField word = new TextField("Word");
    private final Select<SpellCheckLanguage> language = new Select<>();
    private final TextArea comment = new TextArea("Comment (optional)");

    private WordDialog(VocabularyService service, VocabularyWord existing, Runnable onSaved, boolean delete) {
        this.service = service;
        this.existing = existing;
        this.onSaved = onSaved;
        this.deleteMode = delete;
        setWidth("min(28rem, 95vw)");
        comment.setMaxLength(VocabularyService.MAX_COMMENT_LENGTH);
        comment.setWidthFull();
        Button cancel = new Button("Cancel", e -> close());

        if (delete) {
            setHeaderTitle("Delete word");
            comment.setPlaceholder("For example: added by mistake, should be DataTek");
            add(new VerticalLayout(new Paragraph("Delete \"" + existing.getWord() + "\" ("
                    + scopeLabel(existing.getLanguage().orElse(null)) + ") from the dictionary for all users?"),
                    comment));
            Button confirm = new Button("Delete", e -> run(() -> service.delete(existing.getId(),
                    existing.getVersion(), comment.getValue()), "\"" + existing.getWord() + "\" was deleted"));
            confirm.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
            getFooter().add(cancel, confirm);
            return;
        }

        setHeaderTitle(existing == null ? "Add word" : "Edit word");
        word.setRequired(true);
        word.setMaxLength(VocabularyService.MAX_WORD_LENGTH);
        word.setWidthFull();
        word.setHelperText("Case-sensitive: \"Datatek\" and \"DataTek\" are different words.");
        language.setLabel("Applies to");
        language.setItems(SpellCheckLanguage.values());
        language.setItemLabelGenerator(WordDialog::scopeLabel);
        language.setEmptySelectionAllowed(true);
        language.setEmptySelectionCaption(scopeLabel(null));
        language.setWidthFull();
        comment.setPlaceholder("For example why the word is needed or changed");
        if (existing != null) {
            word.setValue(existing.getWord());
            language.setValue(existing.getLanguage().orElse(null));
        }
        add(new VerticalLayout(word, language, comment));

        Button save = new Button(existing == null ? "Add" : "Save", e -> save());
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickShortcut(Key.ENTER);
        getFooter().add(cancel, save);
    }

    static void openAdd(VocabularyService service, Runnable onSaved) {
        new WordDialog(service, null, onSaved, false).open();
    }

    static void openEdit(VocabularyService service, VocabularyWord word, Runnable onSaved) {
        new WordDialog(service, word, onSaved, false).open();
    }

    static void openDelete(VocabularyService service, VocabularyWord word, Runnable onSaved) {
        new WordDialog(service, word, onSaved, true).open();
    }

    /** "All languages" for {@code null}, otherwise the language name. */
    static String scopeLabel(SpellCheckLanguage language) {
        return language == null ? "All languages" : language.getDisplayName();
    }

    private void save() {
        if (existing == null) {
            run(() -> service.add(word.getValue(), language.getValue(), comment.getValue()),
                    "\"" + word.getValue().strip() + "\" was added");
        } else {
            run(() -> service.update(existing.getId(), existing.getVersion(), word.getValue(), language.getValue(),
                    comment.getValue()), "\"" + word.getValue().strip() + "\" was saved");
        }
    }

    private void run(Runnable action, String success) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            if (deleteMode) {
                Notification.show(e.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            } else {
                word.setErrorMessage(e.getMessage());
                word.setInvalid(true);
            }
            return;
        } catch (IllegalStateException e) {
            Notification.show(e.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.ERROR);
            close();
            onSaved.run();
            return;
        }
        Notification.show(success, 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.SUCCESS);
        close();
        onSaved.run();
    }
}

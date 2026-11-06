package com.github.avec112.spellcheck;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Lets the user add a word to the {@link SharedSpellCheckVocabulary}. Opened
 * from the "Add to dictionary" action of a spell-checked field.
 * <p>
 * The word is editable, so its spelling or capitalization can be corrected
 * before it is shared with everybody ("Datatek" → "DataTek").
 */
final class AddSpellCheckWordDialog extends Dialog {

    private enum Scope {
        ALL_LANGUAGES, FIELD_LANGUAGE
    }

    private final SharedSpellCheckVocabulary vocabulary;
    private final SpellCheckLanguage fieldLanguage;
    private final TextField word = new TextField("Word");
    private final RadioButtonGroup<Scope> scope = new RadioButtonGroup<>("Applies to");
    private final TextArea comment = new TextArea("Comment (optional)");

    AddSpellCheckWordDialog(SharedSpellCheckVocabulary vocabulary, String suggestedWord,
            SpellCheckLanguage fieldLanguage) {
        this.vocabulary = vocabulary;
        this.fieldLanguage = fieldLanguage;
        setHeaderTitle("Add to dictionary");
        setWidth("min(28rem, 95vw)");

        word.setValue(suggestedWord);
        word.setRequired(true);
        word.setMaxLength(100);
        word.setWidthFull();
        word.setHelperText("The word is added for all users. Check the spelling and capitalization.");

        scope.setItems(Scope.values());
        scope.setItemLabelGenerator(s -> s == Scope.ALL_LANGUAGES ? "All languages"
                : "Only " + fieldLanguage.getDisplayName());
        scope.setValue(Scope.ALL_LANGUAGES);

        comment.setMaxLength(500);
        comment.setWidthFull();
        comment.setPlaceholder("For example why the word is needed");

        VerticalLayout content = new VerticalLayout(word, scope, comment);
        content.setPadding(false);
        add(content);

        Button save = new Button("Add", e -> save());
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickShortcut(Key.ENTER);
        Button cancel = new Button("Cancel", e -> close());
        getFooter().add(cancel, save);
    }

    private void save() {
        String value = word.getValue().strip();
        try {
            vocabulary.addWord(value, scope.getValue() == Scope.ALL_LANGUAGES ? null : fieldLanguage,
                    comment.getValue());
        } catch (IllegalArgumentException e) {
            word.setErrorMessage(e.getMessage());
            word.setInvalid(true);
            return;
        }
        Notification.show("\"" + value + "\" was added to the dictionary", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.SUCCESS);
        close();
    }
}

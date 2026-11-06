package com.github.avec112.spellcheck;

import java.io.Serializable;
import java.util.stream.Collectors;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.shared.Registration;

/**
 * Sends the {@link SharedSpellCheckVocabulary} to one browser tab (UI) and
 * keeps it up to date.
 * <p>
 * There is one instance per UI, created when the first spell-checked field is
 * attached, not one per field: the word list is sent to the browser once and
 * shared by all fields and the spell-check worker. When any user changes the
 * vocabulary, the new list is pushed to every open UI (the application uses
 * {@code @Push}), and all fields re-check their text.
 */
final class SharedVocabularySync implements Serializable {

    private final UI ui;
    private final SharedSpellCheckVocabulary vocabulary;
    private final Registration changeRegistration;

    private SharedVocabularySync(UI ui, SharedSpellCheckVocabulary vocabulary) {
        this.ui = ui;
        this.vocabulary = vocabulary;
        this.changeRegistration = vocabulary.addChangeListener(this::onVocabularyChanged);
        // The vocabulary lives as long as the application; the UI does not.
        ui.addDetachListener(event -> changeRegistration.remove());
        send();
    }

    /** Starts synchronizing the UI, unless it already is. */
    static void ensure(UI ui, SharedSpellCheckVocabulary vocabulary) {
        if (ComponentUtil.getData(ui, SharedVocabularySync.class) == null) {
            ComponentUtil.setData(ui, SharedVocabularySync.class, new SharedVocabularySync(ui, vocabulary));
        }
    }

    private void onVocabularyChanged() {
        try {
            // Called from another user's thread: modify this UI only under its lock.
            ui.access(this::send);
        } catch (UIDetachedException e) {
            changeRegistration.remove();
        }
    }

    /**
     * One line per word, "language TAB word", where the language is "*" for
     * all languages. Words never contain whitespace, so no escaping is needed.
     * The page module (spell-check-client.ts) picks it up from the window
     * object, also if it is loaded after this script runs.
     */
    private void send() {
        String payload = vocabulary.getWords().stream()
                .map(w -> (w.appliesToAllLanguages() ? "*" : w.language().getCode()) + "\t" + w.word())
                .collect(Collectors.joining("\n"));
        ui.getPage().executeJs("""
                window.__spellCheckSharedVocabulary = $0;
                window.dispatchEvent(new CustomEvent('spell-check-shared-vocabulary'));""", payload);
    }
}

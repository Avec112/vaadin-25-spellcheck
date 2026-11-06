package com.github.avec112.spellcheck;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.shared.Registration;

/**
 * A vocabulary shared by all spell-checked fields of the application, such as
 * the organization's product names and abbreviations.
 * <p>
 * The spell-check components only read the words, listen for changes and let
 * users add words. Where and how the words are stored is up to the
 * application, which implements this interface (for example with a database)
 * and {@linkplain #install(VaadinContext, SharedSpellCheckVocabulary) installs}
 * it once at startup. Without an installed vocabulary, the components behave
 * exactly as before and offer no "Add to dictionary" action.
 * <p>
 * Implementations must be thread-safe: they are called from the request
 * threads of all users.
 */
public interface SharedSpellCheckVocabulary extends Serializable {

    /**
     * A shared word.
     *
     * @param word     the word, case-sensitive
     * @param language the language it applies to, or {@code null} for all
     *                 languages
     */
    record Word(String word, SpellCheckLanguage language) implements Serializable {

        public Word {
            Objects.requireNonNull(word, "word must not be null");
        }

        /** Whether the word applies to all languages. */
        public boolean appliesToAllLanguages() {
            return language == null;
        }
    }

    /**
     * Returns all shared words. Called whenever a UI needs the current list,
     * so implementations should answer from memory rather than query a
     * database each time.
     *
     * @return the words, never {@code null}
     */
    List<Word> getWords();

    /**
     * Adds a word on behalf of the current user.
     *
     * @param word     the word, a single word without whitespace
     * @param language the language, or {@code null} for all languages
     * @param comment  optional reason, may be {@code null}
     * @throws IllegalArgumentException with a message that can be shown to the
     *                                  user, if the word is invalid or already
     *                                  exists
     */
    void addWord(String word, SpellCheckLanguage language, String comment);

    /**
     * Registers a listener that is called after the words have changed (from
     * any user's thread). The components use it to update all open fields.
     *
     * @param listener the listener
     * @return a registration for removing the listener
     */
    Registration addChangeListener(SerializableRunnable listener);

    /**
     * Makes the vocabulary available to all spell-check components of the
     * application. Typically called from a {@code VaadinServiceInitListener}:
     *
     * <pre>{@code
     * @Bean
     * VaadinServiceInitListener spellCheckVocabulary(MyVocabulary vocabulary) {
     *     return event -> SharedSpellCheckVocabulary.install(event.getSource().getContext(), vocabulary);
     * }
     * }</pre>
     *
     * @param context    the application's Vaadin context
     * @param vocabulary the vocabulary
     */
    static void install(VaadinContext context, SharedSpellCheckVocabulary vocabulary) {
        context.setAttribute(Holder.class, new Holder(Objects.requireNonNull(vocabulary)));
    }

    /**
     * Returns the vocabulary installed for the current Vaadin service, if any.
     *
     * @return the installed vocabulary
     */
    static Optional<SharedSpellCheckVocabulary> current() {
        VaadinService service = VaadinService.getCurrent();
        if (service == null) {
            return Optional.empty();
        }
        Holder holder = service.getContext().getAttribute(Holder.class);
        return Optional.ofNullable(holder).map(Holder::vocabulary);
    }

    /** Context attribute wrapper, so the attribute key is not the interface itself. */
    record Holder(SharedSpellCheckVocabulary vocabulary) implements Serializable {
    }
}

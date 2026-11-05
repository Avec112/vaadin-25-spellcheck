package com.github.avec112.spellcheck;

import java.util.Set;

import com.vaadin.flow.component.HasElement;

/**
 * A field with offline spell checking. Implemented by {@link MyCustomTextArea}
 * and {@link MyCustomTextField}, so code can configure spell checking without
 * knowing the concrete field type (for example, for every field in a form).
 */
public interface HasSpellCheck extends HasElement {

    /**
     * Enables or disables spell checking. Disabled by default. Disabling removes
     * the spell-check indicator and any displayed results.
     *
     * @param enabled {@code true} to enable spell checking
     */
    void setSpellCheckEnabled(boolean enabled);

    /**
     * Returns whether spell checking is enabled.
     *
     * @return {@code true} if spell checking is enabled
     */
    boolean isSpellCheckEnabled();

    /**
     * Sets the spell-check language. Can be changed at any time; the text is
     * re-checked immediately when spell checking is enabled.
     *
     * @param language the language, not {@code null}
     * @throws NullPointerException if {@code language} is {@code null}
     */
    void setSpellCheckLanguage(SpellCheckLanguage language);

    /**
     * Returns the spell-check language. Defaults to
     * {@link SpellCheckLanguage#NB_NO}.
     *
     * @return the language, never {@code null}
     */
    SpellCheckLanguage getSpellCheckLanguage();

    /**
     * Adds a word that this field accepts as correctly spelled, such as an
     * internal abbreviation or product name. The word applies to this field
     * instance only and never modifies the shared dictionary. A word starting
     * with a lowercase letter is also accepted when capitalized at the start of
     * a sentence; otherwise matching is case-sensitive.
     *
     * @param word a single word without whitespace, not {@code null}
     * @throws NullPointerException     if {@code word} is {@code null}
     * @throws IllegalArgumentException if {@code word} is blank or contains
     *                                  whitespace
     */
    void addSpellCheckWord(String word);

    /**
     * Returns the custom words added with {@link #addSpellCheckWord(String)}.
     *
     * @return an unmodifiable view of the custom words, in insertion order
     */
    Set<String> getSpellCheckWords();
}

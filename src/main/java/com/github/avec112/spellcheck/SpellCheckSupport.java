package com.github.avec112.spellcheck;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.dom.Element;

/**
 * Spell-check state and wiring shared by {@link MyCustomTextArea} and
 * {@link MyCustomTextField}.
 * <p>
 * Spell checking runs entirely in the browser. This class only keeps the
 * server-side configuration and mirrors it onto a
 * {@code <spell-check-indicator>} element (implemented in
 * {@code src/main/frontend/spellcheck/spell-check-indicator.ts}) that is placed
 * in the field's {@code suffix} slot while spell checking is enabled. Removing
 * the element is what disables spell checking: the browser-side element then
 * stops listening, cancels pending work and hides its UI.
 * <p>
 * The indicator is a bare {@link Element} rather than a {@link Component}, so
 * {@code getSuffixComponent()} on the host field keeps returning the
 * application's own suffix component (or {@code null}).
 */
final class SpellCheckSupport implements Serializable {

    static final String INDICATOR_TAG = "spell-check-indicator";
    static final String SUFFIX_SLOT = "suffix";

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private final Component field;
    private final Element indicator = new Element(INDICATOR_TAG);
    private final Set<String> customWords = new LinkedHashSet<>();

    private boolean enabled;
    private SpellCheckLanguage language = SpellCheckLanguage.NB_NO;

    SpellCheckSupport(Component field) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        indicator.setProperty("language", language.getCode());
        indicator.setProperty("customWords", "");
    }

    void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            appendIndicator();
        } else {
            indicator.removeFromParent();
        }
    }

    boolean isEnabled() {
        return enabled;
    }

    void setLanguage(SpellCheckLanguage language) {
        this.language = Objects.requireNonNull(language,
                "Spell-check language must not be null");
        indicator.setProperty("language", language.getCode());
    }

    SpellCheckLanguage getLanguage() {
        return language;
    }

    void addWord(String word) {
        Objects.requireNonNull(word, "Spell-check word must not be null");
        String normalized = word.strip();
        if (normalized.isEmpty() || WHITESPACE.matcher(normalized).find()) {
            throw new IllegalArgumentException(
                    "Spell-check word must be a single non-blank word: '"
                            + word + "'");
        }
        if (customWords.add(normalized)) {
            // Newline-separated: a word can never contain whitespace.
            indicator.setProperty("customWords",
                    String.join("\n", customWords));
        }
    }

    Set<String> getWords() {
        return Collections.unmodifiableSet(customWords);
    }

    /**
     * Must be called after the host field's suffix slot has been replaced,
     * because {@code setSuffixComponent} removes every element in that slot,
     * including the indicator.
     */
    void suffixChanged() {
        if (enabled) {
            appendIndicator();
        }
    }

    /** Package-private for tests. */
    Element getIndicator() {
        return indicator;
    }

    private void appendIndicator() {
        // Always last, so the application's own suffix component stays first
        // in the slot and is still returned by getSuffixComponent().
        indicator.removeFromParent();
        indicator.setAttribute("slot", SUFFIX_SLOT);
        field.getElement().appendChild(indicator);
    }
}

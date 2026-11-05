package com.github.avec112.spellcheck;

import java.util.Set;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.textfield.TextArea;

/**
 * A standard Vaadin {@link TextArea} with optional, fully offline spell checking.
 * <p>
 * Spell checking is disabled by default and uses
 * {@link SpellCheckLanguage#NB_NO Norwegian Bokmål} unless another language is
 * set. When enabled, a compact indicator in the field's suffix area shows the
 * number of possible misspellings and opens a popover with suggested
 * corrections. All checking happens in the browser against bundled Hunspell
 * dictionaries; no server round trips or external services are involved.
 * <p>
 * Everything else (value handling, {@code Binder}, validation, read-only,
 * enabled state, theming) is inherited unchanged from {@link TextArea}.
 *
 * <pre>{@code
 * MyCustomTextArea field = new MyCustomTextArea("Description");
 * field.setSpellCheckEnabled(true);
 * field.setSpellCheckLanguage(SpellCheckLanguage.EN_GB); // optional
 * }</pre>
 */
@JsModule("./spellcheck/spell-check-indicator.ts")
@NpmPackage(value = "nspell", version = "2.1.5")
public class MyCustomTextArea extends TextArea implements HasSpellCheck {

    private final SpellCheckSupport spellCheck = new SpellCheckSupport(this);

    /** Creates an empty text area without a label. */
    public MyCustomTextArea() {
        super();
    }

    /**
     * Creates an empty text area with a label.
     *
     * @param label the label text
     */
    public MyCustomTextArea(String label) {
        super(label);
    }

    /**
     * Creates an empty text area with a label and placeholder.
     *
     * @param label       the label text
     * @param placeholder the placeholder text
     */
    public MyCustomTextArea(String label, String placeholder) {
        super(label, placeholder);
    }

    /**
     * Creates a text area with a label, initial value and placeholder.
     *
     * @param label        the label text
     * @param initialValue the initial value
     * @param placeholder  the placeholder text
     */
    public MyCustomTextArea(String label, String initialValue, String placeholder) {
        super(label, initialValue, placeholder);
    }

    @Override
    public void setSpellCheckEnabled(boolean enabled) {
        spellCheck.setEnabled(enabled);
    }

    @Override
    public boolean isSpellCheckEnabled() {
        return spellCheck.isEnabled();
    }

    @Override
    public void setSpellCheckLanguage(SpellCheckLanguage language) {
        spellCheck.setLanguage(language);
    }

    @Override
    public SpellCheckLanguage getSpellCheckLanguage() {
        return spellCheck.getLanguage();
    }

    @Override
    public void addSpellCheckWord(String word) {
        spellCheck.addWord(word);
    }

    @Override
    public Set<String> getSpellCheckWords() {
        return spellCheck.getWords();
    }

    @Override
    public void setSuffixComponent(Component component) {
        super.setSuffixComponent(component);
        // Replacing the suffix slot also removes the spell-check indicator.
        spellCheck.suffixChanged();
    }

    /** Package-private for tests. */
    SpellCheckSupport getSpellCheckSupport() {
        return spellCheck;
    }
}

package com.github.avec112.spellcheck;

import java.util.Arrays;
import java.util.Optional;

/**
 * Languages supported by the offline spell checker.
 * <p>
 * Each constant maps to a Hunspell dictionary bundled with the frontend (see
 * {@code src/main/frontend/spellcheck/dictionaries}). The {@linkplain #getCode()
 * code} is a BCP 47 language tag and is also the key the browser uses to pick
 * the dictionary.
 */
public enum SpellCheckLanguage {

    /** Norwegian Bokmål. The default language. */
    NB_NO("nb-NO", "Norwegian Bokmål"),

    /** American English. */
    EN_US("en-US", "English (US)"),

    /** British English ("-ise" spelling). */
    EN_GB("en-GB", "English (UK)");

    private final String code;
    private final String displayName;

    SpellCheckLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * Returns the BCP 47 language tag, for example {@code nb-NO}.
     *
     * @return the language code, never {@code null}
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns a human-readable name, for example {@code Norwegian Bokmål}.
     *
     * @return the display name, never {@code null}
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Finds the language with the given {@linkplain #getCode() code}.
     *
     * @param code a language code such as {@code nb-NO}
     * @return the language, or empty if the code is unknown or {@code null}
     */
    public static Optional<SpellCheckLanguage> fromCode(String code) {
        return Arrays.stream(values()).filter(l -> l.code.equals(code)).findFirst();
    }
}

package com.github.avec112.spellcheck;

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
    NB_NO("nb-NO"),

    /** American English. */
    EN_US("en-US"),

    /** British English ("-ise" spelling). */
    EN_GB("en-GB");

    private final String code;

    SpellCheckLanguage(String code) {
        this.code = code;
    }

    /**
     * Returns the BCP 47 language tag, for example {@code nb-NO}.
     *
     * @return the language code, never {@code null}
     */
    public String getCode() {
        return code;
    }
}

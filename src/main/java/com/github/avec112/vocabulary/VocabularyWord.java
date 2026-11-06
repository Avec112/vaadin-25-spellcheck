package com.github.avec112.vocabulary;

import java.time.Instant;
import java.util.Optional;

import com.github.avec112.spellcheck.SpellCheckLanguage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A word in the shared spell-check vocabulary.
 */
@Entity
@Table(name = "spellcheck_word")
public class VocabularyWord {

    /** Stored {@link #getLanguageScope() language scope} for "all languages". */
    public static final String ALL_LANGUAGES = "ALL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String word;

    @Column(name = "language_scope", nullable = false, length = 10)
    private String languageScope;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected VocabularyWord() {
        // for JPA
    }

    VocabularyWord(String word, SpellCheckLanguage language, String user, Instant now) {
        this.word = word;
        this.languageScope = scopeOf(language);
        this.createdBy = user;
        this.createdAt = now;
        this.updatedBy = user;
        this.updatedAt = now;
    }

    void update(String word, SpellCheckLanguage language, String user, Instant now) {
        this.word = word;
        this.languageScope = scopeOf(language);
        this.updatedBy = user;
        this.updatedAt = now;
    }

    /** Converts a language ({@code null} for all languages) to the stored scope. */
    static String scopeOf(SpellCheckLanguage language) {
        return language == null ? ALL_LANGUAGES : language.getCode();
    }

    public Long getId() {
        return id;
    }

    public String getWord() {
        return word;
    }

    /** Either {@link #ALL_LANGUAGES} or a language code such as {@code nb-NO}. */
    public String getLanguageScope() {
        return languageScope;
    }

    /** The language, or empty if the word applies to all languages. */
    public Optional<SpellCheckLanguage> getLanguage() {
        return SpellCheckLanguage.fromCode(languageScope);
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}

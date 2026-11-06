package com.github.avec112.vocabulary;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One entry in the change log of the shared vocabulary: who added, changed or
 * deleted which word, when, and why. Entries are never modified or deleted.
 */
@Entity
@Table(name = "spellcheck_word_change")
public class VocabularyWordChange {

    public enum Action {
        ADDED, UPDATED, DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word_id", nullable = false, updatable = false)
    private Long wordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Action action;

    @Column(nullable = false, updatable = false, length = 100)
    private String word;

    @Column(name = "language_scope", nullable = false, updatable = false, length = 10)
    private String languageScope;

    @Column(name = "previous_word", updatable = false, length = 100)
    private String previousWord;

    @Column(name = "previous_language_scope", updatable = false, length = 10)
    private String previousLanguageScope;

    @Column(updatable = false, length = 500)
    private String comment;

    @Column(name = "changed_by", nullable = false, updatable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected VocabularyWordChange() {
        // for JPA
    }

    VocabularyWordChange(VocabularyWord word, Action action, String previousWord, String previousLanguageScope,
            String comment, String user, Instant now) {
        this.wordId = word.getId();
        this.action = action;
        this.word = word.getWord();
        this.languageScope = word.getLanguageScope();
        this.previousWord = previousWord;
        this.previousLanguageScope = previousLanguageScope;
        this.comment = comment;
        this.changedBy = user;
        this.changedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getWordId() {
        return wordId;
    }

    public Action getAction() {
        return action;
    }

    public String getWord() {
        return word;
    }

    public String getLanguageScope() {
        return languageScope;
    }

    public String getPreviousWord() {
        return previousWord;
    }

    public String getPreviousLanguageScope() {
        return previousLanguageScope;
    }

    public String getComment() {
        return comment;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}

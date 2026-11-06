package com.github.avec112.vocabulary;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.github.avec112.spellcheck.SharedSpellCheckVocabulary;
import com.github.avec112.spellcheck.SpellCheckLanguage;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.shared.Registration;

/**
 * The organization's shared spell-check vocabulary, stored in the database.
 * <p>
 * Every user can add, change and delete words. Each change is recorded in a
 * change log with the user, the time and an optional comment, so mistakes
 * can be traced and corrected ("Datatek" → "DataTek").
 * <p>
 * The words are cached in memory, because every open browser tab reads the
 * list. After a change is committed, the cache is refreshed and all
 * registered listeners (one per open browser tab, see the spell-check
 * components) are notified, so every user's fields update immediately.
 * <p>
 * The cache and listeners live in this JVM. If the application runs on
 * several nodes, other nodes see a change after their own next change or
 * restart. See the spell-check README for options.
 */
@Service
public class VocabularyService implements SharedSpellCheckVocabulary {

    static final int MAX_WORD_LENGTH = 100;
    static final int MAX_COMMENT_LENGTH = 500;
    private static final int HISTORY_LIMIT = 500;
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Logger LOG = LoggerFactory.getLogger(VocabularyService.class);

    private final transient VocabularyWordRepository words;
    private final transient VocabularyWordChangeRepository changes;
    private final transient List<SerializableRunnable> listeners = new CopyOnWriteArrayList<>();
    private transient volatile List<Word> cache;

    VocabularyService(VocabularyWordRepository words, VocabularyWordChangeRepository changes) {
        this.words = words;
        this.changes = changes;
    }

    // --- SharedSpellCheckVocabulary (used by the spell-check components) ---

    @Override
    public List<Word> getWords() {
        List<Word> current = cache;
        if (current == null) {
            current = words.findAll().stream()
                    .map(w -> new Word(w.getWord(), w.getLanguage().orElse(null)))
                    .toList();
            cache = current;
        }
        return current;
    }

    @Override
    @Transactional
    public void addWord(String word, SpellCheckLanguage language, String comment) {
        add(word, language, comment);
    }

    @Override
    public Registration addChangeListener(SerializableRunnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    // --- Management (used by the vocabulary view) ---

    /**
     * Returns the words whose text contains {@code filter} (ignoring case),
     * sorted alphabetically. A blank filter returns all words.
     */
    @Transactional(readOnly = true)
    public List<VocabularyWord> findWords(String filter) {
        return filter == null || filter.isBlank() ? words.findAllByOrderByWordAsc()
                : words.findByWordContainingIgnoreCaseOrderByWordAsc(filter.strip());
    }

    /**
     * Adds a word for the current user.
     *
     * @throws IllegalArgumentException with a user-facing message if the word
     *                                  is invalid or already exists
     */
    @Transactional
    public VocabularyWord add(String word, SpellCheckLanguage language, String comment) {
        String normalized = validateWord(word);
        String normalizedComment = validateComment(comment);
        ensureNotPresent(normalized, language, null);
        Instant now = Instant.now();
        String user = currentUser();
        VocabularyWord entity = saveAndFlush(new VocabularyWord(normalized, language, user, now));
        changes.save(new VocabularyWordChange(entity, VocabularyWordChange.Action.ADDED, null, null,
                normalizedComment, user, now));
        afterCommit();
        return entity;
    }

    /**
     * Changes a word's text and/or language.
     *
     * @param id              the word's id
     * @param expectedVersion the version the user edited; protects against
     *                        overwriting a concurrent change
     * @throws IllegalArgumentException with a user-facing message if the new
     *                                  values are invalid or already exist
     * @throws IllegalStateException    if the word was changed or deleted by
     *                                  someone else in the meantime
     */
    @Transactional
    public VocabularyWord update(long id, long expectedVersion, String word, SpellCheckLanguage language,
            String comment) {
        String normalized = validateWord(word);
        String normalizedComment = validateComment(comment);
        VocabularyWord entity = findCurrent(id, expectedVersion);
        String previousWord = entity.getWord();
        String previousScope = entity.getLanguageScope();
        if (previousWord.equals(normalized) && previousScope.equals(VocabularyWord.scopeOf(language))) {
            return entity; // nothing changed
        }
        ensureNotPresent(normalized, language, id);
        Instant now = Instant.now();
        String user = currentUser();
        entity.update(normalized, language, user, now);
        entity = saveAndFlush(entity);
        changes.save(new VocabularyWordChange(entity, VocabularyWordChange.Action.UPDATED, previousWord,
                previousScope, normalizedComment, user, now));
        afterCommit();
        return entity;
    }

    /**
     * Deletes a word. The change log keeps the deleted word.
     *
     * @throws IllegalStateException if the word was changed or deleted by
     *                               someone else in the meantime
     */
    @Transactional
    public void delete(long id, long expectedVersion, String comment) {
        String normalizedComment = validateComment(comment);
        VocabularyWord entity = findCurrent(id, expectedVersion);
        words.delete(entity);
        changes.save(new VocabularyWordChange(entity, VocabularyWordChange.Action.DELETED, null, null,
                normalizedComment, currentUser(), Instant.now()));
        afterCommit();
    }

    /** The most recent changes, newest first. */
    @Transactional(readOnly = true)
    public List<VocabularyWordChange> recentChanges() {
        return changes.findAllByOrderByChangedAtDescIdDesc(Limit.of(HISTORY_LIMIT));
    }

    /** All changes of one word (also after it was deleted), newest first. */
    @Transactional(readOnly = true)
    public List<VocabularyWordChange> changesOf(long wordId) {
        return changes.findByWordIdOrderByChangedAtDescIdDesc(wordId);
    }

    // --- Internals ---

    /**
     * Drops the in-memory copy of the words. Only needed when the table was
     * changed outside this service (scripts, tests); open fields are not
     * notified.
     */
    public void clearCache() {
        cache = null;
    }

    private static String validateWord(String word) {
        String normalized = word == null ? "" : word.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Enter a word");
        }
        if (WHITESPACE.matcher(normalized).find()) {
            throw new IllegalArgumentException("Enter a single word without spaces");
        }
        if (normalized.length() > MAX_WORD_LENGTH) {
            throw new IllegalArgumentException("The word can be at most " + MAX_WORD_LENGTH + " characters");
        }
        return normalized;
    }

    private static String validateComment(String comment) {
        String normalized = comment == null ? "" : comment.strip();
        if (normalized.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("The comment can be at most " + MAX_COMMENT_LENGTH + " characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Rejects exact duplicates, and words for one language that already exist
     * for all languages. Matching is case-sensitive: "Datatek" and "DataTek"
     * are different words.
     */
    private void ensureNotPresent(String word, SpellCheckLanguage language, Long ignoreId) {
        String scope = VocabularyWord.scopeOf(language);
        for (VocabularyWord existing : words.findByWord(word)) {
            if (existing.getId().equals(ignoreId)) {
                continue;
            }
            if (existing.getLanguageScope().equals(scope)) {
                throw new IllegalArgumentException("\"" + word + "\" is already in the dictionary"
                        + (language == null ? "" : " for " + language.getDisplayName()));
            }
            if (existing.getLanguageScope().equals(VocabularyWord.ALL_LANGUAGES)) {
                throw new IllegalArgumentException("\"" + word + "\" is already in the dictionary for all languages");
            }
        }
    }

    private VocabularyWord findCurrent(long id, long expectedVersion) {
        VocabularyWord entity = words.findById(id).orElseThrow(
                () -> new IllegalStateException("The word was deleted by someone else. Refresh the list."));
        if (entity.getVersion() != expectedVersion) {
            throw new IllegalStateException("The word was changed by someone else. Refresh the list and try again.");
        }
        return entity;
    }

    /** Saves and flushes, turning a lost race on the unique constraint into a user-facing error. */
    private VocabularyWord saveAndFlush(VocabularyWord entity) {
        try {
            return words.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("\"" + entity.getWord() + "\" is already in the dictionary", e);
        }
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    /** Refreshes the cache and notifies listeners once the change is committed. */
    private void afterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache = null;
                for (SerializableRunnable listener : listeners) {
                    try {
                        listener.run();
                    } catch (RuntimeException e) {
                        LOG.warn("Spell-check vocabulary listener failed", e);
                    }
                }
            }
        });
    }
}

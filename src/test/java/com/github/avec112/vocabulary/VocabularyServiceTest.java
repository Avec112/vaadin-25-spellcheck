package com.github.avec112.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.avec112.TestcontainersConfiguration;
import com.github.avec112.spellcheck.SharedSpellCheckVocabulary.Word;
import com.github.avec112.spellcheck.SpellCheckLanguage;
import com.github.avec112.vocabulary.VocabularyWordChange.Action;
import com.vaadin.flow.shared.Registration;

/** Runs against a real Postgres (Testcontainers) with the Flyway schema. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@WithMockUser("anna")
class VocabularyServiceTest {

    @Autowired
    VocabularyService service;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE spellcheck_word, spellcheck_word_change");
        service.clearCache();
    }

    @Test
    void addedWordsAreSharedWithTheirLanguageAndLogged() {
        service.add("DataTek", null, "Our product");
        service.add("saksbehandlingssystem", SpellCheckLanguage.NB_NO, null);

        assertEquals(List.of(new Word("DataTek", null), new Word("saksbehandlingssystem", SpellCheckLanguage.NB_NO)),
                service.getWords().stream().sorted((a, b) -> a.word().compareTo(b.word())).toList());

        VocabularyWord word = service.findWords("datatek").getFirst();
        assertEquals("anna", word.getCreatedBy());
        assertEquals(VocabularyWord.ALL_LANGUAGES, word.getLanguageScope());

        VocabularyWordChange change = service.changesOf(word.getId()).getFirst();
        assertEquals(Action.ADDED, change.getAction());
        assertEquals("anna", change.getChangedBy());
        assertEquals("Our product", change.getComment());
    }

    @Test
    void wordsAreValidatedAndNormalized() {
        assertThrows(IllegalArgumentException.class, () -> service.add("  ", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.add("two words", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.add("x".repeat(101), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.add("ok", null, "c".repeat(501)));

        service.add("  Fjordlyx ", null, "   ");
        VocabularyWord word = service.findWords("Fjordlyx").getFirst();
        assertEquals("Fjordlyx", word.getWord());
        assertNull(service.changesOf(word.getId()).getFirst().getComment(), "blank comment is stored as null");
    }

    @Test
    void duplicatesAreRejectedButCaseMatters() {
        service.add("DataTek", null, null);

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> service.add("DataTek", null, null));
        assertTrue(duplicate.getMessage().contains("already in the dictionary"));
        // Already covered by the all-languages entry.
        assertThrows(IllegalArgumentException.class, () -> service.add("DataTek", SpellCheckLanguage.EN_US, null));
        // A different capitalization is a different word.
        service.add("Datatek", null, null);
        assertEquals(2, service.findWords("datatek").size());
    }

    @Test
    void theSameWordCanBeAddedForSeveralLanguages() {
        service.add("Fjordlyx", SpellCheckLanguage.NB_NO, null);
        service.add("Fjordlyx", SpellCheckLanguage.EN_GB, null);
        assertThrows(IllegalArgumentException.class, () -> service.add("Fjordlyx", SpellCheckLanguage.NB_NO, null));
        assertEquals(2, service.findWords("Fjordlyx").size());
    }

    @Test
    @WithMockUser("bjorn")
    void updatingAWordRecordsBeforeAndAfterWithComment() {
        VocabularyWord added = service.add("Datatek", SpellCheckLanguage.NB_NO, null);

        VocabularyWord updated = service.update(added.getId(), added.getVersion(), "DataTek", null,
                "Wrong capitalization");

        assertEquals("DataTek", updated.getWord());
        assertEquals("bjorn", updated.getUpdatedBy());
        assertEquals(List.of(new Word("DataTek", null)), service.getWords());
        VocabularyWordChange change = service.changesOf(added.getId()).getFirst();
        assertEquals(Action.UPDATED, change.getAction());
        assertEquals("Datatek", change.getPreviousWord());
        assertEquals("nb-NO", change.getPreviousLanguageScope());
        assertEquals("DataTek", change.getWord());
        assertEquals(VocabularyWord.ALL_LANGUAGES, change.getLanguageScope());
        assertEquals("Wrong capitalization", change.getComment());
    }

    @Test
    void staleUpdatesAndDeletesAreRejected() {
        VocabularyWord added = service.add("Datatek", null, null);
        service.update(added.getId(), added.getVersion(), "DataTek", null, null);

        assertThrows(IllegalStateException.class,
                () -> service.update(added.getId(), added.getVersion(), "DATATEK", null, null));
        assertThrows(IllegalStateException.class, () -> service.delete(added.getId(), added.getVersion(), null));
    }

    @Test
    void updateCannotCreateADuplicate() {
        service.add("DataTek", null, null);
        VocabularyWord other = service.add("Datatek", null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.update(other.getId(), other.getVersion(), "DataTek", null, null));
    }

    @Test
    void deletedWordsDisappearButTheirHistoryRemains() {
        VocabularyWord added = service.add("Datatek", null, null);
        service.delete(added.getId(), added.getVersion(), "Added by mistake, should be DataTek");

        assertTrue(service.getWords().isEmpty());
        assertTrue(service.findWords("").isEmpty());
        List<VocabularyWordChange> history = service.changesOf(added.getId());
        assertEquals(List.of(Action.DELETED, Action.ADDED), history.stream().map(VocabularyWordChange::getAction).toList());
        assertEquals("Added by mistake, should be DataTek", history.getFirst().getComment());
        assertEquals("Datatek", history.getFirst().getWord());
        assertEquals(2, service.recentChanges().size());
    }

    @Test
    void listenersAreNotifiedAfterCommitOnly() {
        AtomicInteger notifications = new AtomicInteger();
        Registration registration = service.addChangeListener(notifications::incrementAndGet);

        service.add("DataTek", null, null);
        assertEquals(1, notifications.get());
        assertThrows(IllegalArgumentException.class, () -> service.add("DataTek", null, null));
        assertEquals(1, notifications.get(), "a rejected change notifies nobody");

        registration.remove();
        service.add("Fjordlyx", null, null);
        assertEquals(1, notifications.get());
    }
}

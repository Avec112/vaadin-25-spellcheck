package com.github.avec112.spellcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.avec112.TestcontainersConfiguration;
import com.github.avec112.vocabulary.VocabularyService;
import com.github.avec112.views.SpellCheckDemoView;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;

/**
 * The spell-check components together with the application's database-backed
 * vocabulary (browserless, real Postgres).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@WithMockUser("anna")
class SharedVocabularyIntegrationTest extends SpringBrowserlessTest {

    @Autowired
    VocabularyService vocabulary;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE spellcheck_word, spellcheck_word_change");
        vocabulary.clearCache();
    }

    @Test
    void attachedFieldsOfferAddToDictionary() {
        navigate(SpellCheckDemoView.class);
        MyCustomTextField field = $(MyCustomTextField.class).first();
        assertTrue(field.getSpellCheckSupport().getIndicator().getProperty("canAddWords", false));
    }

    @Test
    void dialogAddsTheCorrectedWordForAllLanguages() {
        navigate(SpellCheckDemoView.class);
        new AddSpellCheckWordDialog(vocabulary, "Datatek", SpellCheckLanguage.NB_NO).open();

        test($(TextField.class).withCaption("Word").single()).setValue("DataTek");
        test($(Button.class).withText("Add").single()).click();

        assertTrue(vocabulary.getWords().contains(new SharedSpellCheckVocabulary.Word("DataTek", null)));
        assertEquals("anna", vocabulary.findWords("DataTek").getFirst().getCreatedBy());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dialogCanLimitTheWordToTheFieldLanguage() {
        navigate(SpellCheckDemoView.class);
        new AddSpellCheckWordDialog(vocabulary, "saksbehandlingssystem", SpellCheckLanguage.NB_NO).open();

        RadioButtonGroup<Object> scope = $(RadioButtonGroup.class).withCaption("Applies to").single();
        test(scope).selectItem("Only Norwegian Bokmål");
        test($(Button.class).withText("Add").single()).click();

        assertTrue(vocabulary.getWords()
                .contains(new SharedSpellCheckVocabulary.Word("saksbehandlingssystem", SpellCheckLanguage.NB_NO)));
    }

    @Test
    void dialogShowsDuplicatesAsAFieldError() {
        vocabulary.add("DataTek", null, null);
        navigate(SpellCheckDemoView.class);
        new AddSpellCheckWordDialog(vocabulary, "DataTek", SpellCheckLanguage.NB_NO).open();

        test($(Button.class).withText("Add").single()).click();

        TextField word = $(TextField.class).withCaption("Word").single();
        assertTrue(word.isInvalid());
        assertTrue(word.getErrorMessage().contains("already in the dictionary"));
        assertFalse($(TextField.class).withCaption("Word").all().isEmpty(), "dialog stays open");
    }
}

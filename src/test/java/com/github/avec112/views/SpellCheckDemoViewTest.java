package com.github.avec112.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.avec112.TestcontainersConfiguration;
import com.github.avec112.spellcheck.MyCustomTextArea;
import com.github.avec112.spellcheck.MyCustomTextField;
import com.github.avec112.spellcheck.SpellCheckLanguage;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@WithMockUser("anna")
class SpellCheckDemoViewTest extends SpringBrowserlessTest {

    @Test
    void demoContainsIndependentlyConfiguredFields() {
        navigate(SpellCheckDemoView.class);

        List<MyCustomTextArea> areas = $(MyCustomTextArea.class).all();
        assertEquals(List.of(SpellCheckLanguage.NB_NO, SpellCheckLanguage.EN_US, SpellCheckLanguage.EN_GB,
                SpellCheckLanguage.NB_NO), areas.stream().map(MyCustomTextArea::getSpellCheckLanguage).toList());
        assertTrue(areas.stream().allMatch(MyCustomTextArea::isSpellCheckEnabled));

        List<MyCustomTextField> fields = $(MyCustomTextField.class).all();
        assertTrue(fields.stream().allMatch(MyCustomTextField::isSpellCheckEnabled));
        assertTrue(fields.stream().allMatch(f -> f.getSpellCheckLanguage() == SpellCheckLanguage.NB_NO));
        assertEquals(1, fields.stream().filter(f -> !f.getSpellCheckWords().isEmpty()).count());
    }

    @Test
    @SuppressWarnings("unchecked")
    void playgroundControlsToggleSpellCheckAndLanguage() {
        navigate(SpellCheckDemoView.class);
        MyCustomTextArea playground = $(MyCustomTextArea.class).withCaption("Playground").single();

        test($(Checkbox.class).withCaption("Spell checking").single()).click();
        assertFalse(playground.isSpellCheckEnabled());

        RadioButtonGroup<SpellCheckLanguage> language = $(RadioButtonGroup.class).single();
        test(language).selectItem("en-GB");
        assertEquals(SpellCheckLanguage.EN_GB, playground.getSpellCheckLanguage());
    }
}

package com.github.avec112.spellcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.dom.Element;

/**
 * Tests the public spell-check API of both components through
 * {@link HasSpellCheck}, so every behavior is verified for {@link MyCustomTextArea} and
 * {@link MyCustomTextField}.
 */
class SpellCheckComponentsTest {

    private static List<Element> indicators(HasSpellCheck field) {
        return field.getElement().getChildren()
                .filter(child -> SpellCheckSupport.INDICATOR_TAG.equals(child.getTag()))
                .toList();
    }

    static Stream<Arguments> fields() {
        return Stream.of(
                Arguments.of(Named.<Supplier<HasSpellCheck>>of("MyCustomTextArea", MyCustomTextArea::new)),
                Arguments.of(Named.<Supplier<HasSpellCheck>>of("MyCustomTextField", MyCustomTextField::new)));
    }

    @Test
    void componentsExtendStandardVaadinFields() {
        assertTrue(TextArea.class.isAssignableFrom(MyCustomTextArea.class));
        assertTrue(TextField.class.isAssignableFrom(MyCustomTextField.class));
    }

    @Test
    void languageCodes() {
        assertEquals("nb-NO", SpellCheckLanguage.NB_NO.getCode());
        assertEquals("en-US", SpellCheckLanguage.EN_US.getCode());
        assertEquals("en-GB", SpellCheckLanguage.EN_GB.getCode());
    }

    @ParameterizedTest
    @MethodSource("fields")
    void spellCheckIsDisabledByDefaultWithNorwegianBokmal(Supplier<HasSpellCheck> factory) {
        HasSpellCheck field = factory.get();

        assertFalse(field.isSpellCheckEnabled());
        assertEquals(SpellCheckLanguage.NB_NO, field.getSpellCheckLanguage());
        assertTrue(indicators(field).isEmpty(), "no spell-check UI while disabled");
    }

    @ParameterizedTest
    @MethodSource("fields")
    void enablingAddsIndicatorToSuffixSlotAndDisablingRemovesIt(Supplier<HasSpellCheck> factory) {
        HasSpellCheck field = factory.get();

        field.setSpellCheckEnabled(true);
        assertTrue(field.isSpellCheckEnabled());
        assertEquals(1, indicators(field).size());
        Element indicator = indicators(field).getFirst();
        assertEquals("suffix", indicator.getAttribute("slot"));
        assertEquals("nb-NO", indicator.getProperty("language"));

        // Enabling twice must not add a second indicator.
        field.setSpellCheckEnabled(true);
        assertEquals(1, indicators(field).size());

        field.setSpellCheckEnabled(false);
        assertFalse(field.isSpellCheckEnabled());
        assertTrue(indicators(field).isEmpty());

        field.setSpellCheckEnabled(true);
        assertEquals(1, indicators(field).size());
    }

    @ParameterizedTest
    @MethodSource("fields")
    void languageCanBeChangedBeforeAndAfterEnabling(Supplier<HasSpellCheck> factory) {
        HasSpellCheck field = factory.get();

        field.setSpellCheckLanguage(SpellCheckLanguage.EN_GB);
        field.setSpellCheckEnabled(true);
        assertEquals(SpellCheckLanguage.EN_GB, field.getSpellCheckLanguage());
        assertEquals("en-GB", indicators(field).getFirst().getProperty("language"));

        field.setSpellCheckLanguage(SpellCheckLanguage.EN_US);
        assertEquals(SpellCheckLanguage.EN_US, field.getSpellCheckLanguage());
        assertEquals("en-US", indicators(field).getFirst().getProperty("language"));
    }

    @ParameterizedTest
    @MethodSource("fields")
    void nullLanguageIsRejectedAndKeepsPreviousLanguage(Supplier<HasSpellCheck> factory) {
        HasSpellCheck field = factory.get();
        field.setSpellCheckLanguage(SpellCheckLanguage.EN_US);

        NullPointerException error = assertThrows(NullPointerException.class, () -> field.setSpellCheckLanguage(null));
        assertTrue(error.getMessage().contains("language"));
        assertEquals(SpellCheckLanguage.EN_US, field.getSpellCheckLanguage());
    }

    @ParameterizedTest
    @MethodSource("fields")
    void componentsKeepIndependentConfiguration(Supplier<HasSpellCheck> factory) {
        HasSpellCheck first = factory.get();
        HasSpellCheck second = factory.get();

        first.setSpellCheckEnabled(true);
        first.setSpellCheckLanguage(SpellCheckLanguage.EN_GB);
        first.addSpellCheckWord("Fjordsoft");

        assertFalse(second.isSpellCheckEnabled());
        assertEquals(SpellCheckLanguage.NB_NO, second.getSpellCheckLanguage());
        assertTrue(second.getSpellCheckWords().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("fields")
    void customWordsAreValidatedAndSentToTheBrowser(Supplier<HasSpellCheck> factory) {
        HasSpellCheck field = factory.get();
        field.setSpellCheckEnabled(true);

        field.addSpellCheckWord("Fjordsoft");
        field.addSpellCheckWord("  KundeNav ");
        field.addSpellCheckWord("Fjordsoft"); // duplicate, ignored

        assertEquals(List.of("Fjordsoft", "KundeNav"), List.copyOf(field.getSpellCheckWords()));
        assertEquals("Fjordsoft\nKundeNav", indicators(field).getFirst().getProperty("customWords"));
        assertThrows(UnsupportedOperationException.class, () -> field.getSpellCheckWords().add("x"));

        assertThrows(NullPointerException.class, () -> field.addSpellCheckWord(null));
        assertThrows(IllegalArgumentException.class, () -> field.addSpellCheckWord("   "));
        assertThrows(IllegalArgumentException.class, () -> field.addSpellCheckWord("two words"));
        assertThrows(IllegalArgumentException.class, () -> field.addSpellCheckWord("line\nbreak"));
    }

    @Test
    void textAreaAndTextFieldDoNotShareState() {
        MyCustomTextArea area = new MyCustomTextArea("Area");
        MyCustomTextField field = new MyCustomTextField("Field");

        area.setSpellCheckEnabled(true);
        area.setSpellCheckLanguage(SpellCheckLanguage.EN_US);

        assertFalse(field.isSpellCheckEnabled());
        assertEquals(SpellCheckLanguage.NB_NO, field.getSpellCheckLanguage());
    }

    @Test
    void applicationSuffixComponentIsPreserved() {
        MyCustomTextField field = new MyCustomTextField();
        Span unit = new Span("kr");
        field.setSuffixComponent(unit);
        field.setSpellCheckEnabled(true);

        assertSame(unit, field.getSuffixComponent());
        assertEquals(2, field.getElement().getChildren().filter(e -> "suffix".equals(e.getAttribute("slot"))).count());

        // Replacing the suffix clears the slot; the indicator must survive.
        Span other = new Span("NOK");
        field.setSuffixComponent(other);
        assertSame(other, field.getSuffixComponent());
        assertEquals(field.getElement(), field.getSpellCheckSupport().getIndicator().getParent());

        field.setSuffixComponent(null);
        assertNull(field.getSuffixComponent(), "the indicator is not reported as a suffix component");
        assertEquals(field.getElement(), field.getSpellCheckSupport().getIndicator().getParent());
    }

    @Test
    void standardFieldBehaviorIsUnchanged() {
        MyCustomTextArea area = new MyCustomTextArea("Description", "Initial", "Placeholder");
        area.setSpellCheckEnabled(true);

        assertEquals("Description", area.getLabel());
        assertEquals("Initial", area.getValue());
        assertEquals("Placeholder", area.getPlaceholder());

        area.setValue("Changed");
        assertEquals("Changed", area.getValue());
        area.setReadOnly(true);
        assertTrue(area.isReadOnly());
        area.setEnabled(false);
        assertFalse(area.isEnabled());
        area.setRequiredIndicatorVisible(true);
        assertTrue(area.isRequiredIndicatorVisible());
        // Spell-check configuration is unaffected by these states.
        assertTrue(area.isSpellCheckEnabled());
    }

    @Test
    void binderIntegrationIsUnchanged() {
        MyCustomTextField title = new MyCustomTextField("Title");
        title.setSpellCheckEnabled(true);

        Binder<Holder> binder = new Binder<>(Holder.class);
        binder.forField(title).asRequired("Required").bind(Holder::getValue, Holder::setValue);
        Holder bean = new Holder();
        bean.setValue("Original");
        binder.setBean(bean);

        assertEquals("Original", title.getValue());
        title.setValue("Updated");
        assertEquals("Updated", bean.getValue());

        title.setValue("");
        assertFalse(binder.validate().isOk());
        assertTrue(title.isInvalid());
    }

    /** Bean for the Binder test. */
    public static class Holder {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}

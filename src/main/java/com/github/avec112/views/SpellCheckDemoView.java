package com.github.avec112.views;

import com.github.avec112.spellcheck.MyCustomTextArea;
import com.github.avec112.spellcheck.MyCustomTextField;
import com.github.avec112.spellcheck.SpellCheckLanguage;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

/**
 * Manual test page for the offline spell-checking components.
 */
@Route("spellcheck-demo")
@RouteAlias("")
@PageTitle("Spell check demo")
public class SpellCheckDemoView extends VerticalLayout {

    private static final String NORWEGIAN_TEXT = """
            Jeg spisste frokost på kjøkenbordet før jeg gikk til skolen.
            Reggjeringen la fram et nytt forslag om barnehaageplasser i går.
            Det var en fin dag, og guttene spilte fotball i sollskinnet.""";

    private static final String US_TEXT = """
            I recieve the newsletter every week. The colour of the theatre
            near the city center is definately my favorite.""";

    private static final String GB_TEXT = """
            I recieve the newsletter every week. The color of the theater
            near the city centre is definately my favourite.""";

    public SpellCheckDemoView() {
        setMaxWidth("56rem");
        setPadding(true);

        add(new H1("Offline spell checking"));
        add(new Paragraph("Spelling is checked in the browser about 600 ms after you stop typing. "
                + "Click the \"abc\" indicator in a field to see suggestions, replace a word or ignore it. "
                + "No external services are used."));

        add(new H2("Norwegian Bokmål (TextArea)"));
        MyCustomTextArea norwegian = new MyCustomTextArea("Beskrivelse");
        norwegian.setSpellCheckEnabled(true);
        norwegian.setSpellCheckLanguage(SpellCheckLanguage.NB_NO);
        norwegian.setValue(NORWEGIAN_TEXT);
        norwegian.setWidthFull();
        add(norwegian);

        add(new H2("American English (TextArea)"));
        MyCustomTextArea american = new MyCustomTextArea("Summary (en-US)");
        american.setSpellCheckEnabled(true);
        american.setSpellCheckLanguage(SpellCheckLanguage.EN_US);
        american.setValue(US_TEXT);
        american.setWidthFull();
        add(american);

        add(new H2("British English (TextArea)"));
        MyCustomTextArea british = new MyCustomTextArea("Summary (en-GB)");
        british.setSpellCheckEnabled(true);
        british.setSpellCheckLanguage(SpellCheckLanguage.EN_GB);
        british.setValue(GB_TEXT);
        british.setWidthFull();
        add(british);

        add(new H2("Norwegian TextField with Binder"));
        add(createTextFieldExample());

        add(new H2("Custom vocabulary"));
        add(createCustomVocabularyExample());

        add(new H2("Playground"));
        add(createPlayground());
    }

    private Component createTextFieldExample() {
        MyCustomTextField title = new MyCustomTextField("Tittel");
        title.setSpellCheckEnabled(true);
        title.setWidthFull();
        title.setHelperText("Required. Bound with Binder; the server value updates when you apply a correction.");

        Binder<Document> binder = new Binder<>(Document.class);
        binder.forField(title)
                .asRequired("Tittel er påkrevd")
                .bind(Document::getTitle, Document::setTitle);
        Document document = new Document();
        document.setTitle("Årsrapport for avdelingen for informasjonssikkerhett");
        binder.setBean(document);

        Paragraph serverValue = new Paragraph();
        binder.addValueChangeListener(event -> serverValue.setText("Bean title: " + document.getTitle()));
        serverValue.setText("Bean title: " + document.getTitle());
        return new VerticalLayout(title, serverValue);
    }

    private Component createCustomVocabularyExample() {
        String text = "Fjordlyx lanserer KundeNav i Q3. Fjordlyx-teamet tester nå.";

        MyCustomTextField withWords = new MyCustomTextField("With custom words (Fjordlyx, KundeNav)");
        withWords.setSpellCheckEnabled(true);
        withWords.addSpellCheckWord("Fjordlyx");
        withWords.addSpellCheckWord("KundeNav");
        withWords.setValue(text);
        withWords.setWidthFull();

        MyCustomTextField withoutWords = new MyCustomTextField("Without custom words");
        withoutWords.setSpellCheckEnabled(true);
        withoutWords.setValue(text);
        withoutWords.setWidthFull();

        return new VerticalLayout(withWords, withoutWords);
    }

    private Component createPlayground() {
        MyCustomTextArea field = new MyCustomTextArea("Playground");
        field.setWidthFull();
        field.setSpellCheckEnabled(true);
        field.setValue("Skriv noe her. Prøv å bytte språk: The colour of the centre.");
        field.setValueChangeMode(ValueChangeMode.LAZY);

        Checkbox spellCheck = new Checkbox("Spell checking", true);
        spellCheck.addValueChangeListener(e -> field.setSpellCheckEnabled(e.getValue()));

        RadioButtonGroup<SpellCheckLanguage> language = new RadioButtonGroup<>("Language");
        language.setItems(SpellCheckLanguage.values());
        language.setItemLabelGenerator(SpellCheckLanguage::getCode);
        language.setValue(field.getSpellCheckLanguage());
        language.addValueChangeListener(e -> field.setSpellCheckLanguage(e.getValue()));

        Checkbox readOnly = new Checkbox("Read-only");
        readOnly.addValueChangeListener(e -> field.setReadOnly(e.getValue()));

        Checkbox enabled = new Checkbox("Enabled", true);
        enabled.addValueChangeListener(e -> field.setEnabled(e.getValue()));

        Button setNorwegian = new Button("Set Norwegian text", e -> field.setValue("Dette er en settning med noen feill."));
        Button setEnglish = new Button("Set English text", e -> field.setValue("This sentense has a few erors."));
        Button clear = new Button("Clear", e -> field.clear());

        Pre serverValue = new Pre();
        serverValue.getStyle().set("white-space", "pre-wrap").set("margin", "0");
        field.addValueChangeListener(e -> serverValue.setText("Server value: " + e.getValue()));
        serverValue.setText("Server value: " + field.getValue());

        HorizontalLayout toggles = new HorizontalLayout(spellCheck, readOnly, enabled);
        toggles.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        HorizontalLayout buttons = new HorizontalLayout(setNorwegian, setEnglish, clear);
        buttons.setWrap(true);

        VerticalLayout layout = new VerticalLayout(field, toggles, language, buttons, serverValue);
        layout.setPadding(false);
        return layout;
    }

    /** Simple bean for the Binder example. */
    public static class Document {
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}

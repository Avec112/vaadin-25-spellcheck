package com.github.avec112.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.avec112.TestcontainersConfiguration;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@WithMockUser("anna")
class VocabularyViewTest extends SpringBrowserlessTest {

    private static final int WORD = 0;
    private static final int SCOPE = 1;
    private static final int ADDED_BY = 2;
    private static final int ACTIONS = 5;

    @Autowired
    VocabularyService service;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE spellcheck_word, spellcheck_word_change");
        service.clearCache();
    }

    @SuppressWarnings("unchecked")
    private Grid<VocabularyWord> words() {
        return $(Grid.class).first();
    }

    @SuppressWarnings("unchecked")
    private Grid<VocabularyWordChange> history() {
        return $(Grid.class).last();
    }

    private Button rowButton(int row, String text) {
        HorizontalLayout actions = (HorizontalLayout) test(words()).getCellComponent(row, ACTIONS);
        return actions.getChildren().map(Button.class::cast).filter(b -> b.getText().equals(text)).findFirst()
                .orElseThrow();
    }

    @Test
    void addEditAndDeleteAreLoggedWithComments() {
        navigate(VocabularyView.class);

        // Add
        test($(Button.class).withText("Add word").single()).click();
        test($(TextField.class).withCaption("Word").single()).setValue("Datatek");
        test($(TextArea.class).withCaption("Comment (optional)").single()).setValue("Product name");
        test($(Button.class).withText("Add").single()).click();
        assertEquals(1, test(words()).size());
        assertEquals("Datatek", test(words()).getCellText(0, WORD));
        assertEquals("All languages", test(words()).getCellText(0, SCOPE));
        assertEquals("anna", test(words()).getCellText(0, ADDED_BY));

        // Edit: fix the capitalization
        test(rowButton(0, "Edit")).click();
        test($(TextField.class).withCaption("Word").single()).setValue("DataTek");
        test($(TextArea.class).withCaption("Comment (optional)").single()).setValue("Wrong capitalization");
        test($(Button.class).withText("Save").single()).click();
        assertEquals("DataTek", test(words()).getCellText(0, WORD));

        // The log shows both changes, newest first
        assertEquals(2, test(history()).size());
        assertEquals("Changed Datatek (All languages) to DataTek (All languages)", test(history()).getCellText(0, 2));
        assertEquals("Wrong capitalization", test(history()).getCellText(0, 3));
        assertEquals("Added Datatek (All languages)", test(history()).getCellText(1, 2));

        // Delete
        test(rowButton(0, "Delete")).click();
        test($(TextArea.class).withCaption("Comment (optional)").single()).setValue("No longer used");
        test($(Button.class).withText("Delete").last()).click();
        assertEquals(0, test(words()).size());
        assertEquals("Deleted DataTek (All languages)", test(history()).getCellText(0, 2));
        assertEquals("No longer used", test(history()).getCellText(0, 3));
        assertTrue(service.getWords().isEmpty());
    }

    @Test
    void searchFiltersIgnoringCase() {
        service.add("DataTek", null, null);
        service.add("Fjordlyx", null, null);
        navigate(VocabularyView.class);

        test($(TextField.class).withPropertyValue(TextField::getPlaceholder, "Search").single()).setValue("data");

        assertEquals(1, test(words()).size());
        assertEquals("DataTek", test(words()).getCellText(0, WORD));
    }
}

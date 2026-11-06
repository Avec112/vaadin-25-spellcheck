package com.github.avec112.vocabulary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.avec112.spellcheck.SharedSpellCheckVocabulary;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Makes the database-backed vocabulary available to every spell-checked field
 * in the application.
 */
@Configuration
class VocabularyConfiguration {

    @Bean
    VaadinServiceInitListener spellCheckVocabularyInstaller(VocabularyService vocabulary) {
        return event -> SharedSpellCheckVocabulary.install(event.getSource().getContext(), vocabulary);
    }
}

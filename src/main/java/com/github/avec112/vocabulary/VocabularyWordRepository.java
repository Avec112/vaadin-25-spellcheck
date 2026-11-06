package com.github.avec112.vocabulary;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface VocabularyWordRepository extends JpaRepository<VocabularyWord, Long> {

    List<VocabularyWord> findAllByOrderByWordAsc();

    List<VocabularyWord> findByWordContainingIgnoreCaseOrderByWordAsc(String filter);

    List<VocabularyWord> findByWord(String word);
}

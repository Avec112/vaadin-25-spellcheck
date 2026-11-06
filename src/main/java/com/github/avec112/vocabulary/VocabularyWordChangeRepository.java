package com.github.avec112.vocabulary;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface VocabularyWordChangeRepository extends JpaRepository<VocabularyWordChange, Long> {

    List<VocabularyWordChange> findAllByOrderByChangedAtDescIdDesc(Limit limit);

    List<VocabularyWordChange> findByWordIdOrderByChangedAtDescIdDesc(Long wordId);
}

package com.subshift.backend.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassifierTrainingEmailRepository extends JpaRepository<ClassifierTrainingEmail, Integer> {

    boolean existsByEmailText(String emailText);

    long countByLabel(String label);

    Page<ClassifierTrainingEmail> findByLabel(String label, Pageable pageable);
}

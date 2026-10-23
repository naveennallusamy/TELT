package com.telt.repository;

import com.telt.constants.QuestionStatus;
import com.telt.entity.question.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByTenantIdAndBankIdAndStatusAndApprovedVersionNotNullOrderByQuestionId(UUID tenantId, UUID bankId,
                                                                                       QuestionStatus status);

    Optional<Question> findByQuestionIdAndTenantId(UUID questionId, UUID tenantId);
}

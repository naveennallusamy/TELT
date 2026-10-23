package com.telt.repository;

import com.telt.entity.question.QuestionReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public interface QuestionReviewRepository extends JpaRepository<QuestionReview, UUID> {

    List<QuestionReview> findByTenantIdAndQuestionIdOrderByReviewedAt(UUID tenantId, UUID questionId);
}

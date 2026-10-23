package com.telt.repository;

import com.telt.entity.exam.ExamReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamReviewRepository extends JpaRepository<ExamReview, UUID> {

    List<ExamReview> findByTenantIdAndExamIdOrderByReviewedAt(UUID tenantId, UUID examId);
}

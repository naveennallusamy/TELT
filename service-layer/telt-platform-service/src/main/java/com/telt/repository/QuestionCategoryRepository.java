package com.telt.repository;

import com.telt.entity.question.QuestionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface QuestionCategoryRepository extends JpaRepository<QuestionCategory, UUID> {

    Optional<QuestionCategory> findByCategoryIdAndTenantId(UUID categoryId, UUID tenantId);

    List<QuestionCategory> findByTenantIdOrderByPath(UUID tenantId);

    boolean existsByTenantIdAndParentCategoryIdAndNameIgnoreCase(UUID tenantId, UUID parentCategoryId, String name);
}

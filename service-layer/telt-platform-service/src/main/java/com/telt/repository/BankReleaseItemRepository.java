package com.telt.repository;

import com.telt.entity.question.BankReleaseItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BankReleaseItemRepository extends JpaRepository<BankReleaseItem, UUID> {

    Page<BankReleaseItem> findByReleaseIdOrderByQuestionId(UUID releaseId, Pageable pageable);

    Optional<BankReleaseItem> findByReleaseIdAndQuestionId(UUID releaseId, UUID questionId);
}

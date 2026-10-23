package com.telt.repository;

import com.telt.entity.exam.ExamItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExamItemRepository extends JpaRepository<ExamItem, UUID> {

    List<ExamItem> findByTenantIdAndSectionIdInOrderByPosition(UUID tenantId, List<UUID> sectionIds);
}

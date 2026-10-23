package com.telt.service;

import com.telt.access.QuestionAccess;
import com.telt.constants.Permission;
import com.telt.constants.RecordStatus;
import com.telt.dto.question.*;
import com.telt.entity.question.QuestionBank;
import com.telt.entity.question.QuestionCategory;
import com.telt.entity.tenant.Department;
import com.telt.error.ApiException;
import com.telt.repository.DepartmentRepository;
import com.telt.repository.QuestionBankRepository;
import com.telt.repository.QuestionCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private final QuestionBankRepository bankRepository;
    private final QuestionCategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final QuestionAccess access;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BankResponse> list() {
        return access.visible(bankRepository.findByTenantIdOrderByName(access.ownerTenant())).stream()
                .map(BankResponse::from).toList();
    }

    @Transactional
    public BankResponse create(CreateBankRequest request) {
        UUID tenantId = access.ownerTenant();
        QuestionBank bank = new QuestionBank();
        bank.setTenantId(tenantId);
        if (request.departmentId() != null) {
            Department department = departmentRepository.findByDepartmentIdAndTenantId(request.departmentId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Department"));
            bank.setDepartmentId(department.getDepartmentId());
        }
        access.require(Permission.QBANK_CREATE, bank);
        if (bankRepository.existsByTenantIdAndNameIgnoreCase(tenantId, request.name())) {
            throw ApiException.conflict("Question bank name already exists");
        }
        bank.setName(request.name());
        bank.setDescription(request.description());
        bankRepository.save(bank);
        auditService.record(tenantId, "QBANK_CREATED", "QUESTION_BANK", bank.getBankId(),
                Map.of("name", request.name(), "departmentId", String.valueOf(request.departmentId())));
        return BankResponse.from(bank);
    }

    @Transactional
    public BankResponse update(UUID bankId, UpdateBankRequest request) {
        UUID tenantId = access.ownerTenant();
        QuestionBank bank = load(tenantId, bankId);
        access.require(Permission.QBANK_CREATE, bank);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.name() != null && !request.name().equals(bank.getName())) {
            if (bankRepository.existsByTenantIdAndNameIgnoreCase(tenantId, request.name())) {
                throw ApiException.conflict("Question bank name already exists");
            }
            bank.setName(request.name());
            changes.put("name", request.name());
        }
        if (request.description() != null) {
            bank.setDescription(request.description());
            changes.put("description", "updated");
        }
        if (request.status() != null && request.status() != bank.getStatus()) {
            bank.setStatus(request.status());
            changes.put("status", request.status());
        }
        if (!changes.isEmpty()) {
            auditService.record(tenantId, "QBANK_UPDATED", "QUESTION_BANK", bankId, changes);
        }
        return BankResponse.from(bank);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findByTenantIdOrderByPath(access.ownerTenant()).stream()
                .map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        UUID tenantId = access.ownerTenant();
        String parentPath = "/";
        if (request.parentCategoryId() != null) {
            parentPath = category(tenantId, request.parentCategoryId()).getPath();
        }
        if (categoryRepository.existsByTenantIdAndParentCategoryIdAndNameIgnoreCase(tenantId,
                request.parentCategoryId(), request.name())) {
            throw ApiException.conflict("Category already exists");
        }
        QuestionCategory category = new QuestionCategory();
        category.setTenantId(tenantId);
        category.setParentCategoryId(request.parentCategoryId());
        category.setName(request.name());
        category.setPath(parentPath);
        categoryRepository.saveAndFlush(category);
        category.setPath(parentPath + category.getCategoryId() + "/");
        return CategoryResponse.from(category);
    }

    QuestionBank load(UUID tenantId, UUID bankId) {
        return bankRepository.findByBankIdAndTenantId(bankId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Question bank"));
    }

    QuestionBank loadActive(UUID tenantId, UUID bankId) {
        QuestionBank bank = load(tenantId, bankId);
        if (bank.getStatus() != RecordStatus.ACTIVE) {
            throw ApiException.conflict("Question bank is inactive");
        }
        return bank;
    }

    QuestionCategory category(UUID tenantId, UUID categoryId) {
        return categoryRepository.findByCategoryIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Category"));
    }
}

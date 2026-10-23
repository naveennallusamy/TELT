package com.telt.access;

import com.telt.constants.Permission;
import com.telt.entity.exam.Exam;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Exam permissions are evaluated against the exam's department, like question banks.
 */
@Component
@RequiredArgsConstructor
public class ExamAccess {

    private final AuthorizationService authorizationService;

    public boolean can(Permission permission, Exam exam) {
        AuthorizationSnapshot snapshot = authorizationService.current();
        return exam.getDepartmentId() == null ? snapshot.hasTenantWide(permission)
                : snapshot.allowsDepartment(permission, exam.getDepartmentId());
    }

    public void require(Permission permission, Exam exam) {
        if (!can(permission, exam)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
    }

    public boolean canView(Exam exam) {
        return can(Permission.EXAM_VIEW, exam) || can(Permission.EXAM_EDIT, exam) || can(Permission.EXAM_REVIEW, exam);
    }

    public List<Exam> visible(Collection<Exam> exams) {
        return exams.stream().filter(this::canView).toList();
    }
}

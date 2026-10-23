package com.telt.constants;

/**
 * Fixed permission catalog; mirrored in the {@code permissions} table (Flyway V3).
 */
public enum Permission {
    TENANT_SETTINGS_MANAGE(false),
    DEPARTMENT_MANAGE(false),
    USER_VIEW(false),
    USER_MANAGE(false),
    USER_ACCESS_MODE_MANAGE(false),
    ROLE_ASSIGN(false),
    AUDIT_VIEW(false),
    STUDENT_ENROLL(false),
    STUDENT_VIEW(false),
    GROUP_MANAGE(false),
    QBANK_CREATE(false),
    QUESTION_VIEW(false),
    QUESTION_WRITE(false),
    QUESTION_REVIEW(false),
    QUESTION_PUBLISH(false),
    ANSWER_KEY_VIEW(false),
    EXAM_CREATE(false),
    EXAM_VIEW(false),
    EXAM_EDIT(false),
    EXAM_REVIEW(false),
    EXAM_PUBLISH(false),
    EXAM_ASSIGN(false),
    EXAM_COLLABORATOR_MANAGE(false),
    EXAM_MONITOR_LIVE(false),
    ATTEMPT_ACCOMMODATE(false),
    GRADE_DESCRIPTIVE(false),
    RESPONSE_VIEW(false),
    RESULT_SUBMIT_FOR_APPROVAL(false),
    RESULT_APPROVE(false),
    RESULT_PUBLISH(false),
    REPORT_VIEW(false),
    REPORT_EXPORT(false),
    ATTEMPT_OWN(false),
    RESULT_VIEW_OWN(false),
    PLATFORM_TENANT_MANAGE(true),
    PLATFORM_QBANK_MANAGE(true),
    PLATFORM_QBANK_GRANT(true),
    PLATFORM_BREAK_GLASS(true);

    private final boolean platformOnly;

    Permission(boolean platformOnly) {
        this.platformOnly = platformOnly;
    }

    public boolean isPlatformOnly() {
        return platformOnly;
    }
}

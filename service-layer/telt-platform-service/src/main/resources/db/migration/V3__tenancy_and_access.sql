-- Phase 1: tenant settings, departments, memberships, permission catalog, scoped role assignments, audit log.

ALTER TABLE tenant
    ADD COLUMN sub_admin_default_access_mode VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT_SCOPED',
    ADD COLUMN separation_of_duties          BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_tenant_access_mode CHECK (sub_admin_default_access_mode IN ('TENANT_WIDE', 'DEPARTMENT_SCOPED'));

CREATE TABLE departments (
    department_id        UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    parent_department_id UUID,
    code                 VARCHAR(50)   NOT NULL,
    name                 VARCHAR(200)  NOT NULL,
    path                 VARCHAR(2000) NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(100),
    created_on           TIMESTAMPTZ,
    updated_by           VARCHAR(100),
    updated_on           TIMESTAMPTZ,
    CONSTRAINT uk_departments_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_departments_tenant_id UNIQUE (tenant_id, department_id),
    CONSTRAINT fk_departments_parent FOREIGN KEY (tenant_id, parent_department_id)
        REFERENCES departments (tenant_id, department_id),
    CONSTRAINT ck_departments_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX idx_departments_path ON departments (tenant_id, path);

CREATE TABLE tenant_memberships (
    membership_id   UUID PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    membership_type VARCHAR(20) NOT NULL,
    access_mode     VARCHAR(20),
    department_id   UUID,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100),
    created_on      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    updated_on      TIMESTAMPTZ,
    CONSTRAINT uk_membership_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_membership_department FOREIGN KEY (tenant_id, department_id)
        REFERENCES departments (tenant_id, department_id),
    CONSTRAINT ck_membership_type CHECK (membership_type IN ('TENANT_ADMIN', 'SUB_ADMIN', 'STUDENT')),
    CONSTRAINT ck_membership_access_mode CHECK ((membership_type = 'SUB_ADMIN') = (access_mode IS NOT NULL)),
    CONSTRAINT ck_membership_access_mode_value CHECK (access_mode IS NULL OR access_mode IN ('TENANT_WIDE', 'DEPARTMENT_SCOPED')),
    CONSTRAINT ck_membership_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX idx_membership_user ON tenant_memberships (user_id);
CREATE INDEX idx_membership_department ON tenant_memberships (tenant_id, department_id);

-- A student belongs to exactly one tenant and holds no other membership.
CREATE FUNCTION enforce_single_tenant_student() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM tenant_memberships m
               WHERE m.user_id = NEW.user_id AND m.membership_id <> NEW.membership_id
                 AND (NEW.membership_type = 'STUDENT' OR m.membership_type = 'STUDENT')) THEN
        RAISE EXCEPTION 'A student can belong to only one tenant' USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_single_tenant_student
    BEFORE INSERT OR UPDATE OF user_id, membership_type ON tenant_memberships
    FOR EACH ROW EXECUTE FUNCTION enforce_single_tenant_student();

CREATE TABLE permissions (
    code          VARCHAR(60) PRIMARY KEY,
    platform_only BOOLEAN      NOT NULL DEFAULT FALSE,
    description   VARCHAR(255) NOT NULL
);
INSERT INTO permissions (code, platform_only, description)
VALUES ('TENANT_SETTINGS_MANAGE', FALSE, 'Manage tenant settings'),
       ('DEPARTMENT_MANAGE', FALSE, 'Create and edit departments'),
       ('USER_VIEW', FALSE, 'View members'),
       ('USER_MANAGE', FALSE, 'Create, activate, deactivate and unlock members'),
       ('USER_ACCESS_MODE_MANAGE', FALSE, 'Switch sub-admins between tenant-wide and department-scoped'),
       ('ROLE_ASSIGN', FALSE, 'Assign and revoke roles'),
       ('AUDIT_VIEW', FALSE, 'View the tenant audit log'),
       ('STUDENT_ENROLL', FALSE, 'Enrol students'),
       ('STUDENT_VIEW', FALSE, 'View students'),
       ('GROUP_MANAGE', FALSE, 'Manage student groups'),
       ('QBANK_CREATE', FALSE, 'Create question banks'),
       ('QUESTION_VIEW', FALSE, 'View questions'),
       ('QUESTION_WRITE', FALSE, 'Author questions'),
       ('QUESTION_REVIEW', FALSE, 'Review questions'),
       ('QUESTION_PUBLISH', FALSE, 'Publish question versions'),
       ('ANSWER_KEY_VIEW', FALSE, 'View answer keys'),
       ('EXAM_CREATE', FALSE, 'Create exams'),
       ('EXAM_VIEW', FALSE, 'View exams'),
       ('EXAM_EDIT', FALSE, 'Edit exam drafts'),
       ('EXAM_REVIEW', FALSE, 'Review exams'),
       ('EXAM_PUBLISH', FALSE, 'Publish exams'),
       ('EXAM_ASSIGN', FALSE, 'Assign exams to students'),
       ('EXAM_COLLABORATOR_MANAGE', FALSE, 'Manage exam collaborators'),
       ('EXAM_MONITOR_LIVE', FALSE, 'Monitor live exams'),
       ('ATTEMPT_ACCOMMODATE', FALSE, 'Grant attempt accommodations'),
       ('GRADE_DESCRIPTIVE', FALSE, 'Grade descriptive answers'),
       ('RESPONSE_VIEW', FALSE, 'View student responses'),
       ('RESULT_SUBMIT_FOR_APPROVAL', FALSE, 'Submit results for approval'),
       ('RESULT_APPROVE', FALSE, 'Approve results'),
       ('RESULT_PUBLISH', FALSE, 'Publish results'),
       ('REPORT_VIEW', FALSE, 'View reports'),
       ('REPORT_EXPORT', FALSE, 'Export reports'),
       ('ATTEMPT_OWN', FALSE, 'Take assigned exams'),
       ('RESULT_VIEW_OWN', FALSE, 'View own published results'),
       ('PLATFORM_TENANT_MANAGE', TRUE, 'Create and disable tenants'),
       ('PLATFORM_QBANK_MANAGE', TRUE, 'Author platform question banks'),
       ('PLATFORM_QBANK_GRANT', TRUE, 'Grant platform question banks to tenants'),
       ('PLATFORM_BREAK_GLASS', TRUE, 'Audited access to tenant content');

ALTER TABLE roles
    ADD COLUMN description VARCHAR(500),
    ADD COLUMN assignable  BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE role_permissions (
    role_id         UUID        NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE,
    permission_code VARCHAR(60) NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (role_id, permission_code)
);

-- Users: platform role replaces role_id; tenant link moves to memberships.
ALTER TABLE users ADD COLUMN platform_role VARCHAR(30);
UPDATE users u SET platform_role = 'SUPER_ADMIN' FROM roles r WHERE u.role_id = r.role_id AND r.name = 'SUPER_ADMIN';
INSERT INTO tenant_memberships (membership_id, tenant_id, user_id, membership_type, status, created_by, created_on)
SELECT gen_random_uuid(), u.tenant_id, u.id,
       CASE r.name WHEN 'TENANT_ADMIN' THEN 'TENANT_ADMIN' ELSE 'STUDENT' END, u.status, 'MIGRATION', now()
FROM users u JOIN roles r ON r.role_id = u.role_id
WHERE u.tenant_id IS NOT NULL;
ALTER TABLE users DROP COLUMN role_id, DROP COLUMN tenant_id;
ALTER TABLE users ADD CONSTRAINT ck_users_platform_role CHECK (platform_role IS NULL OR platform_role = 'SUPER_ADMIN');

DELETE FROM roles WHERE name NOT IN ('SUPER_ADMIN', 'TENANT_ADMIN');
UPDATE roles SET assignable = FALSE, description = 'Platform administrator' WHERE name = 'SUPER_ADMIN';
UPDATE roles SET assignable = FALSE, description = 'Full access to the tenant' WHERE name = 'TENANT_ADMIN';
INSERT INTO roles (role_id, name, description, assignable, created_by, created_on)
VALUES (gen_random_uuid(), 'STUDENT', 'Takes exams and views own results', FALSE, 'SYSTEM', now()),
       (gen_random_uuid(), 'QUESTION_AUTHOR', 'Authors questions and banks', TRUE, 'SYSTEM', now()),
       (gen_random_uuid(), 'QUESTION_REVIEWER', 'Reviews and publishes questions', TRUE, 'SYSTEM', now()),
       (gen_random_uuid(), 'EXAM_OWNER', 'Creates, publishes and assigns exams', TRUE, 'SYSTEM', now()),
       (gen_random_uuid(), 'EXAM_REVIEWER', 'Reviews exams', TRUE, 'SYSTEM', now()),
       (gen_random_uuid(), 'PROCTOR', 'Monitors live exams', TRUE, 'SYSTEM', now()),
       (gen_random_uuid(), 'EVALUATOR', 'Grades descriptive answers', TRUE, 'SYSTEM', now()),
       (gen_random_uuid(), 'RESULT_APPROVER', 'Approves and publishes results', TRUE, 'SYSTEM', now()),
       (gen_random_uuid(), 'REPORT_VIEWER', 'Views and exports reports', TRUE, 'SYSTEM', now()),
       (gen_random_uuid(), 'STUDENT_MANAGER', 'Enrols and manages students', TRUE, 'SYSTEM', now());

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.code FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN' AND p.platform_only;
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.code FROM roles r, permissions p
WHERE r.name = 'TENANT_ADMIN' AND NOT p.platform_only AND p.code NOT IN ('ATTEMPT_OWN', 'RESULT_VIEW_OWN');
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, v.code FROM roles r
JOIN (VALUES ('STUDENT', 'ATTEMPT_OWN'), ('STUDENT', 'RESULT_VIEW_OWN'),
       ('QUESTION_AUTHOR', 'QBANK_CREATE'),
       ('QUESTION_AUTHOR', 'QUESTION_VIEW'),
       ('QUESTION_AUTHOR', 'QUESTION_WRITE'),
       ('QUESTION_REVIEWER', 'QUESTION_VIEW'),
       ('QUESTION_REVIEWER', 'QUESTION_REVIEW'),
       ('QUESTION_REVIEWER', 'QUESTION_PUBLISH'),
       ('QUESTION_REVIEWER', 'ANSWER_KEY_VIEW'),
       ('EXAM_OWNER', 'EXAM_CREATE'),
       ('EXAM_OWNER', 'EXAM_VIEW'),
       ('EXAM_OWNER', 'EXAM_EDIT'),
       ('EXAM_OWNER', 'EXAM_PUBLISH'),
       ('EXAM_OWNER', 'EXAM_ASSIGN'),
       ('EXAM_OWNER', 'EXAM_COLLABORATOR_MANAGE'),
       ('EXAM_OWNER', 'QUESTION_VIEW'),
       ('EXAM_REVIEWER', 'EXAM_VIEW'),
       ('EXAM_REVIEWER', 'EXAM_REVIEW'),
       ('EXAM_REVIEWER', 'QUESTION_VIEW'),
       ('PROCTOR', 'EXAM_VIEW'),
       ('PROCTOR', 'EXAM_MONITOR_LIVE'),
       ('PROCTOR', 'ATTEMPT_ACCOMMODATE'),
       ('EVALUATOR', 'EXAM_VIEW'),
       ('EVALUATOR', 'GRADE_DESCRIPTIVE'),
       ('EVALUATOR', 'RESPONSE_VIEW'),
       ('EVALUATOR', 'ANSWER_KEY_VIEW'),
       ('RESULT_APPROVER', 'EXAM_VIEW'),
       ('RESULT_APPROVER', 'RESULT_SUBMIT_FOR_APPROVAL'),
       ('RESULT_APPROVER', 'RESULT_APPROVE'),
       ('RESULT_APPROVER', 'RESULT_PUBLISH'),
       ('RESULT_APPROVER', 'REPORT_VIEW'),
       ('REPORT_VIEWER', 'REPORT_VIEW'),
       ('REPORT_VIEWER', 'REPORT_EXPORT'),
       ('STUDENT_MANAGER', 'USER_VIEW'),
       ('STUDENT_MANAGER', 'USER_MANAGE'),
       ('STUDENT_MANAGER', 'STUDENT_ENROLL'),
       ('STUDENT_MANAGER', 'STUDENT_VIEW'),
       ('STUDENT_MANAGER', 'GROUP_MANAGE')) AS v(role_name, code) ON r.name = v.role_name;

CREATE TABLE role_assignments (
    assignment_id UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    user_id       UUID         NOT NULL,
    role_id       UUID         NOT NULL REFERENCES roles (role_id),
    scope_type    VARCHAR(20)  NOT NULL,
    department_id UUID,
    resource_type VARCHAR(30),
    resource_id   UUID,
    granted_by    VARCHAR(100) NOT NULL,
    granted_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_assignment_membership FOREIGN KEY (tenant_id, user_id)
        REFERENCES tenant_memberships (tenant_id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_department FOREIGN KEY (tenant_id, department_id)
        REFERENCES departments (tenant_id, department_id),
    CONSTRAINT ck_assignment_scope CHECK (
        (scope_type = 'TENANT' AND department_id IS NULL AND resource_id IS NULL AND resource_type IS NULL)
        OR (scope_type = 'DEPARTMENT' AND department_id IS NOT NULL AND resource_id IS NULL AND resource_type IS NULL)
        OR (scope_type = 'RESOURCE' AND department_id IS NULL AND resource_id IS NOT NULL AND resource_type IS NOT NULL))
);
CREATE UNIQUE INDEX ux_role_assignment ON role_assignments
    (tenant_id, user_id, role_id, scope_type,
     COALESCE(department_id, resource_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX idx_role_assignment_user ON role_assignments (tenant_id, user_id);

ALTER TABLE refresh_tokens ADD COLUMN tenant_id UUID REFERENCES tenant (tenant_id) ON DELETE CASCADE;

CREATE TABLE audit_events (
    event_id       UUID PRIMARY KEY,
    tenant_id      UUID REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    actor          VARCHAR(100)  NOT NULL,
    action         VARCHAR(60)   NOT NULL,
    entity_type    VARCHAR(60)   NOT NULL,
    entity_id      VARCHAR(100),
    details        VARCHAR(4000),
    correlation_id VARCHAR(64),
    occurred_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_audit_events_tenant ON audit_events (tenant_id, occurred_at DESC);

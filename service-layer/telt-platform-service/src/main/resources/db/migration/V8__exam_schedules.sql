-- Phase 3b: schedules, assignments, platform exam distribution to tenants.

-- Every student gets all of a section's questions (shuffled per student), so random pick-N pools are removed.
ALTER TABLE exam_sections DROP COLUMN pick_count;

-- A tenant's read-only copy of a distributed platform exam points back at the platform exam.
ALTER TABLE exams ADD COLUMN source_exam_id UUID REFERENCES exams (exam_id);

-- One row per (tenant, platform exam). override_policy says which settings the tenant may change per schedule.
CREATE TABLE exam_distributions (
    distribution_id   UUID PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    source_exam_id    UUID        NOT NULL REFERENCES exams (exam_id),
    source_version_no INTEGER     NOT NULL,
    exam_id           UUID        NOT NULL,
    override_policy   JSONB       NOT NULL,
    -- copied into the snapshots' answer_key_visible; grading always keeps the key.
    answer_keys_visible BOOLEAN   NOT NULL DEFAULT FALSE,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    distributed_by    UUID        NOT NULL,
    distributed_at    TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    row_version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_exam_distribution UNIQUE (tenant_id, source_exam_id),
    CONSTRAINT fk_exam_distribution_copy FOREIGN KEY (tenant_id, exam_id) REFERENCES exams (tenant_id, exam_id)
);

-- Copies of distributed exams are created already published (they were reviewed on the platform).
CREATE OR REPLACE FUNCTION enforce_exam_version_immutable() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status <> 'DRAFT' AND NEW.status <> 'DRAFT' AND (NEW.settings IS DISTINCT FROM OLD.settings
        OR NEW.total_marks IS DISTINCT FROM OLD.total_marks OR NEW.version_no IS DISTINCT FROM OLD.version_no
        OR NEW.exam_id IS DISTINCT FROM OLD.exam_id) THEN
        RAISE EXCEPTION 'Exam version % is % and cannot be modified', OLD.version_no, OLD.status
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'IN_REVIEW')
        OR (OLD.status = 'IN_REVIEW' AND NEW.status IN ('APPROVED', 'DRAFT'))
        OR (OLD.status = 'APPROVED' AND NEW.status = 'PUBLISHED')
        OR (OLD.status = 'PUBLISHED' AND NEW.status = 'RETIRED')
        OR (OLD.status = 'DRAFT' AND NEW.status = 'PUBLISHED' AND EXISTS (
            SELECT 1 FROM exams e WHERE e.tenant_id = NEW.tenant_id AND e.exam_id = NEW.exam_id
                                    AND e.source_exam_id IS NOT NULL))) THEN
        RAISE EXCEPTION 'Invalid exam version transition % -> %', OLD.status, NEW.status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

-- A schedule runs one published version; overrides hold the settings changed for this sitting.
CREATE TABLE exam_schedules (
    schedule_id        UUID PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    exam_id            UUID         NOT NULL,
    version_no         INTEGER      NOT NULL,
    title              VARCHAR(300) NOT NULL,
    starts_at          TIMESTAMPTZ  NOT NULL,
    ends_at            TIMESTAMPTZ  NOT NULL,
    time_zone          VARCHAR(60)  NOT NULL,
    late_entry_minutes INTEGER      NOT NULL DEFAULT 0 CHECK (late_entry_minutes >= 0),
    overrides          JSONB        NOT NULL DEFAULT '{}',
    status             VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    row_version        BIGINT       NOT NULL DEFAULT 0,
    created_by         VARCHAR(100),
    created_on         TIMESTAMPTZ,
    updated_by         VARCHAR(100),
    updated_on         TIMESTAMPTZ,
    CONSTRAINT ck_exam_schedule_window CHECK (ends_at > starts_at),
    CONSTRAINT uk_exam_schedule_tenant UNIQUE (tenant_id, schedule_id),
    CONSTRAINT fk_exam_schedule_version FOREIGN KEY (tenant_id, exam_id, version_no)
        REFERENCES exam_versions (tenant_id, exam_id, version_no)
);
CREATE INDEX idx_exam_schedules_exam ON exam_schedules (tenant_id, exam_id, starts_at);

-- A schedule is assigned to individual students and/or departments (which include their sub-departments).
CREATE TABLE exam_assignments (
    assignment_id UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL,
    schedule_id   UUID        NOT NULL,
    user_id       UUID REFERENCES users (id),
    department_id UUID,
    assigned_by   UUID        NOT NULL,
    assigned_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_exam_assignment_target CHECK ((user_id IS NULL) <> (department_id IS NULL)),
    CONSTRAINT fk_exam_assignment_schedule FOREIGN KEY (tenant_id, schedule_id)
        REFERENCES exam_schedules (tenant_id, schedule_id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_assignment_department FOREIGN KEY (tenant_id, department_id)
        REFERENCES departments (tenant_id, department_id)
);
CREATE UNIQUE INDEX ux_exam_assignment_user ON exam_assignments (tenant_id, schedule_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX ux_exam_assignment_department ON exam_assignments (tenant_id, schedule_id, department_id)
    WHERE department_id IS NOT NULL;
CREATE INDEX idx_exam_assignments_user ON exam_assignments (tenant_id, user_id);

INSERT INTO permissions (code, platform_only, description)
VALUES ('PLATFORM_EXAM_DISTRIBUTE', TRUE, 'Distribute platform exams to tenants');
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, 'PLATFORM_EXAM_DISTRIBUTE' FROM roles r WHERE r.name = 'SUPER_ADMIN';

DO
$$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY ['exam_distributions', 'exam_schedules', 'exam_assignments']
        LOOP
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (telt_rls_bypass() OR tenant_id = telt_current_tenant()) '
                               || 'WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant())', t);
        END LOOP;
    -- Only super admins (RLS bypass) create or change distributions; tenants just read them.
    IF '${app_role}' <> current_user AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
        EXECUTE format('REVOKE DELETE ON exam_distributions FROM %I', '${app_role}');
    END IF;
END;
$$;

-- Phase 4b: result approval/publication for manually released schedules, and per-student extra time.

CREATE TABLE schedule_results (
    schedule_id  UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'PUBLISHED')),
    submitted_by UUID        NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    decided_by   UUID,
    decided_at   TIMESTAMPTZ,
    comment      VARCHAR(2000),
    published_by UUID,
    published_at TIMESTAMPTZ,
    row_version  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_schedule_result_schedule FOREIGN KEY (tenant_id, schedule_id)
        REFERENCES exam_schedules (tenant_id, schedule_id)
);

CREATE TABLE exam_accommodations (
    accommodation_id UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    schedule_id      UUID        NOT NULL,
    user_id          UUID        NOT NULL REFERENCES users (id),
    extra_minutes    INTEGER     NOT NULL CHECK (extra_minutes BETWEEN 1 AND 600),
    reason           VARCHAR(500),
    granted_by       UUID        NOT NULL,
    granted_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_exam_accommodation UNIQUE (tenant_id, schedule_id, user_id),
    CONSTRAINT fk_exam_accommodation_schedule FOREIGN KEY (tenant_id, schedule_id)
        REFERENCES exam_schedules (tenant_id, schedule_id)
);

ALTER TABLE exam_attempts ADD COLUMN extra_minutes INTEGER NOT NULL DEFAULT 0;
CREATE INDEX ix_exam_attempt_open_deadline ON exam_attempts (deadline_at) WHERE status = 'IN_PROGRESS';

DO
$$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY ['schedule_results', 'exam_accommodations']
        LOOP
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (telt_rls_bypass() OR tenant_id = telt_current_tenant()) '
                               || 'WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant())', t);
        END LOOP;
    IF '${app_role}' <> current_user AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
        EXECUTE format('REVOKE DELETE ON schedule_results FROM %I', '${app_role}');
    END IF;
END;
$$;

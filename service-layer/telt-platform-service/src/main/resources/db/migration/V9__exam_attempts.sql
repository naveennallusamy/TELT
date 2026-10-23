-- Phase 4a: attempts. Each attempt freezes the schedule's effective settings and its own question/option order.

CREATE TABLE exam_attempts (
    attempt_id         UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    schedule_id        UUID          NOT NULL,
    exam_id            UUID          NOT NULL,
    version_no         INTEGER       NOT NULL,
    user_id            UUID          NOT NULL REFERENCES users (id),
    attempt_no         INTEGER       NOT NULL CHECK (attempt_no > 0),
    status             VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS'
                       CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    settings           JSONB         NOT NULL,
    started_at         TIMESTAMPTZ   NOT NULL,
    deadline_at        TIMESTAMPTZ   NOT NULL,
    current_section    INTEGER       NOT NULL DEFAULT 0,
    section_started_at TIMESTAMPTZ   NOT NULL,
    submitted_at       TIMESTAMPTZ,
    score              NUMERIC(10, 2),
    max_score          NUMERIC(10, 2) NOT NULL,
    grading_status     VARCHAR(20)   NOT NULL DEFAULT 'NOT_GRADED'
                       CHECK (grading_status IN ('NOT_GRADED', 'PENDING_MANUAL', 'GRADED')),
    row_version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_exam_attempt_tenant UNIQUE (tenant_id, attempt_id),
    CONSTRAINT uk_exam_attempt_no UNIQUE (tenant_id, schedule_id, user_id, attempt_no),
    CONSTRAINT fk_exam_attempt_schedule FOREIGN KEY (tenant_id, schedule_id)
        REFERENCES exam_schedules (tenant_id, schedule_id),
    CONSTRAINT fk_exam_attempt_version FOREIGN KEY (tenant_id, exam_id, version_no)
        REFERENCES exam_versions (tenant_id, exam_id, version_no)
);

-- At most one open attempt per student and schedule.
CREATE UNIQUE INDEX uk_exam_attempt_open ON exam_attempts (tenant_id, schedule_id, user_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_exam_attempt_user ON exam_attempts (tenant_id, user_id);

-- One row per question in the attempt, created at start in the student's (possibly shuffled) order.
CREATE TABLE attempt_answers (
    answer_id     UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL,
    attempt_id    UUID        NOT NULL,
    snapshot_id   UUID        NOT NULL REFERENCES exam_question_snapshots (snapshot_id),
    section_index INTEGER     NOT NULL,
    position      INTEGER     NOT NULL,
    option_order  JSONB,
    response      JSONB,
    saved_at      TIMESTAMPTZ,
    marks_awarded NUMERIC(8, 2),
    auto_graded   BOOLEAN     NOT NULL DEFAULT FALSE,
    graded_by     UUID,
    graded_at     TIMESTAMPTZ,
    feedback      VARCHAR(4000),
    CONSTRAINT uk_attempt_answer UNIQUE (attempt_id, snapshot_id),
    CONSTRAINT fk_attempt_answer_attempt FOREIGN KEY (tenant_id, attempt_id)
        REFERENCES exam_attempts (tenant_id, attempt_id) ON DELETE CASCADE
);

DO
$$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY ['exam_attempts', 'attempt_answers']
        LOOP
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (telt_rls_bypass() OR tenant_id = telt_current_tenant()) '
                               || 'WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant())', t);
        END LOOP;
    IF '${app_role}' <> current_user AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
        EXECUTE format('REVOKE DELETE ON exam_attempts, attempt_answers FROM %I', '${app_role}');
    END IF;
END;
$$;

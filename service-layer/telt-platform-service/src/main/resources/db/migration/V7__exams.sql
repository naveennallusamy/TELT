-- Phase 3a: exams with immutable versions, sections and items, review workflow and publish-time question snapshots.
-- Platform exams live in the same tables, owned by the platform tenant.

CREATE TABLE exams (
    exam_id           UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    department_id     UUID,
    code              VARCHAR(50)  NOT NULL,
    title             VARCHAR(300) NOT NULL,
    description       VARCHAR(2000),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    latest_version    INTEGER      NOT NULL DEFAULT 0,
    published_version INTEGER,
    row_version       BIGINT       NOT NULL DEFAULT 0,
    created_by        VARCHAR(100),
    created_on        TIMESTAMPTZ,
    updated_by        VARCHAR(100),
    updated_on        TIMESTAMPTZ,
    CONSTRAINT uk_exam_tenant_id UNIQUE (tenant_id, exam_id),
    CONSTRAINT fk_exam_department FOREIGN KEY (tenant_id, department_id)
        REFERENCES departments (tenant_id, department_id)
);
CREATE UNIQUE INDEX ux_exam_tenant_code ON exams (tenant_id, lower(code));

CREATE TABLE exam_versions (
    version_id   UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL,
    exam_id      UUID          NOT NULL,
    version_no   INTEGER       NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'RETIRED')),
    settings     JSONB         NOT NULL,
    total_marks  NUMERIC(10, 2),
    authored_by  UUID          NOT NULL,
    created_on   TIMESTAMPTZ   NOT NULL,
    submitted_at TIMESTAMPTZ,
    reviewed_by  UUID,
    reviewed_at  TIMESTAMPTZ,
    published_by UUID,
    published_at TIMESTAMPTZ,
    CONSTRAINT uk_exam_version UNIQUE (tenant_id, exam_id, version_no),
    CONSTRAINT fk_exam_version_exam FOREIGN KEY (tenant_id, exam_id)
        REFERENCES exams (tenant_id, exam_id) ON DELETE CASCADE
);

-- pick_count: when set, each attempt draws that many of the section's items at random.
-- time_limit_minutes: optional per-section timer; the section locks when it runs out (exam duration still caps the total).
CREATE TABLE exam_sections (
    section_id UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    exam_id    UUID         NOT NULL,
    version_no INTEGER      NOT NULL,
    position   INTEGER      NOT NULL,
    title      VARCHAR(300) NOT NULL,
    pick_count INTEGER CHECK (pick_count IS NULL OR pick_count > 0),
    time_limit_minutes INTEGER CHECK (time_limit_minutes IS NULL OR time_limit_minutes > 0),
    CONSTRAINT uk_exam_section_tenant UNIQUE (tenant_id, section_id),
    CONSTRAINT uk_exam_section_position UNIQUE (tenant_id, exam_id, version_no, position),
    CONSTRAINT fk_exam_section_version FOREIGN KEY (tenant_id, exam_id, version_no)
        REFERENCES exam_versions (tenant_id, exam_id, version_no) ON DELETE CASCADE
);

-- An item points at an approved version of the owner's question, or at a platform question released through a grant.
CREATE TABLE exam_items (
    item_id             UUID PRIMARY KEY,
    tenant_id           UUID    NOT NULL,
    section_id          UUID    NOT NULL,
    position            INTEGER NOT NULL,
    source_tenant_id    UUID    NOT NULL,
    question_id         UUID    NOT NULL,
    question_version_no INTEGER NOT NULL,
    grant_id            UUID REFERENCES platform_bank_grants (grant_id),
    marks               NUMERIC(8, 2) CHECK (marks IS NULL OR marks >= 0),
    negative_marks      NUMERIC(8, 2) CHECK (negative_marks IS NULL OR negative_marks >= 0),
    CONSTRAINT uk_exam_item_position UNIQUE (tenant_id, section_id, position),
    CONSTRAINT uk_exam_item_question UNIQUE (tenant_id, section_id, question_id),
    CONSTRAINT fk_exam_item_section FOREIGN KEY (tenant_id, section_id)
        REFERENCES exam_sections (tenant_id, section_id) ON DELETE CASCADE,
    CONSTRAINT ck_exam_item_source CHECK ((source_tenant_id = tenant_id) = (grant_id IS NULL))
);

-- Frozen copy of every question when a version is published; attempts and grading read only these rows.
CREATE TABLE exam_question_snapshots (
    snapshot_id        UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    exam_id            UUID          NOT NULL,
    version_no         INTEGER       NOT NULL,
    section_id         UUID          NOT NULL,
    item_id            UUID          NOT NULL,
    position           INTEGER       NOT NULL,
    question_type      VARCHAR(30)   NOT NULL,
    content            JSONB         NOT NULL,
    answer_key         JSONB,
    explanation        TEXT,
    -- false for platform questions whose grant hid answer keys from tenant staff; grading still uses the key.
    answer_key_visible BOOLEAN       NOT NULL DEFAULT TRUE,
    marks              NUMERIC(8, 2) NOT NULL,
    negative_marks     NUMERIC(8, 2) NOT NULL,
    source_tenant_id   UUID          NOT NULL,
    source_question_id UUID          NOT NULL,
    source_version_no  INTEGER       NOT NULL,
    CONSTRAINT uk_exam_snapshot_item UNIQUE (tenant_id, exam_id, version_no, item_id),
    CONSTRAINT fk_exam_snapshot_version FOREIGN KEY (tenant_id, exam_id, version_no)
        REFERENCES exam_versions (tenant_id, exam_id, version_no) ON DELETE CASCADE
);
CREATE INDEX idx_exam_snapshots_version ON exam_question_snapshots (tenant_id, exam_id, version_no, section_id, position);

CREATE TABLE exam_reviews (
    review_id   UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL,
    exam_id     UUID          NOT NULL,
    version_no  INTEGER       NOT NULL,
    reviewer_id UUID          NOT NULL,
    decision    VARCHAR(20)   NOT NULL,
    comments    VARCHAR(2000),
    reviewed_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_exam_review_version FOREIGN KEY (tenant_id, exam_id, version_no)
        REFERENCES exam_versions (tenant_id, exam_id, version_no) ON DELETE CASCADE
);

-- Settings are frozen once a version leaves DRAFT; a rejected review sends the version back to DRAFT.
CREATE FUNCTION enforce_exam_version_immutable() RETURNS trigger LANGUAGE plpgsql AS
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
        OR (OLD.status = 'PUBLISHED' AND NEW.status = 'RETIRED')) THEN
        RAISE EXCEPTION 'Invalid exam version transition % -> %', OLD.status, NEW.status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_exam_version_immutable BEFORE UPDATE ON exam_versions
    FOR EACH ROW EXECUTE FUNCTION enforce_exam_version_immutable();

-- Sections and items can only change while their version is a draft.
CREATE FUNCTION enforce_exam_structure_draft() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
    r      RECORD;
    status VARCHAR(20);
BEGIN
    IF TG_OP = 'DELETE' THEN r := OLD; ELSE r := NEW; END IF;
    IF TG_TABLE_NAME = 'exam_sections' THEN
        SELECT v.status INTO status FROM exam_versions v
        WHERE v.tenant_id = r.tenant_id AND v.exam_id = r.exam_id AND v.version_no = r.version_no;
    ELSE
        SELECT v.status INTO status FROM exam_sections s
        JOIN exam_versions v ON v.tenant_id = s.tenant_id AND v.exam_id = s.exam_id AND v.version_no = s.version_no
        WHERE s.tenant_id = r.tenant_id AND s.section_id = r.section_id;
    END IF;
    IF status IS NOT NULL AND status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Exam version is % and its structure cannot be modified', status
            USING ERRCODE = 'check_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_exam_section_draft BEFORE INSERT OR UPDATE OR DELETE ON exam_sections
    FOR EACH ROW EXECUTE FUNCTION enforce_exam_structure_draft();
CREATE TRIGGER trg_exam_item_draft BEFORE INSERT OR UPDATE OR DELETE ON exam_items
    FOR EACH ROW EXECUTE FUNCTION enforce_exam_structure_draft();

CREATE FUNCTION enforce_exam_snapshot_immutable() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Exam question snapshots are immutable' USING ERRCODE = 'check_violation';
END;
$$;
CREATE TRIGGER trg_exam_snapshot_immutable BEFORE UPDATE ON exam_question_snapshots
    FOR EACH ROW EXECUTE FUNCTION enforce_exam_snapshot_immutable();

DO
$$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY ['exams', 'exam_versions', 'exam_sections', 'exam_items', 'exam_question_snapshots',
        'exam_reviews']
        LOOP
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (telt_rls_bypass() OR tenant_id = telt_current_tenant()) '
                               || 'WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant())', t);
        END LOOP;
    IF '${app_role}' <> current_user AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
        EXECUTE format('REVOKE UPDATE, DELETE ON exam_question_snapshots, exam_reviews FROM %I', '${app_role}');
        EXECUTE format('REVOKE DELETE ON exam_versions FROM %I', '${app_role}');
    END IF;
END;
$$;

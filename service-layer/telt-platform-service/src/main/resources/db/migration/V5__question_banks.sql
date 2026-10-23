-- Phase 2a: tenant question banks, category tree, questions with immutable versions and review workflow.

CREATE TABLE question_banks (
    bank_id       UUID PRIMARY KEY,
    tenant_id     UUID          NOT NULL REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    department_id UUID,
    name          VARCHAR(200)  NOT NULL,
    description   VARCHAR(1000),
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(100),
    created_on    TIMESTAMPTZ,
    updated_by    VARCHAR(100),
    updated_on    TIMESTAMPTZ,
    CONSTRAINT uk_bank_tenant_id UNIQUE (tenant_id, bank_id),
    CONSTRAINT fk_bank_department FOREIGN KEY (tenant_id, department_id)
        REFERENCES departments (tenant_id, department_id)
);
CREATE UNIQUE INDEX ux_bank_tenant_name ON question_banks (tenant_id, lower(name));

CREATE TABLE question_categories (
    category_id        UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    parent_category_id UUID,
    name               VARCHAR(200)  NOT NULL,
    path               VARCHAR(2000) NOT NULL,
    created_by         VARCHAR(100),
    created_on         TIMESTAMPTZ,
    updated_by         VARCHAR(100),
    updated_on         TIMESTAMPTZ,
    CONSTRAINT uk_category_tenant_id UNIQUE (tenant_id, category_id),
    CONSTRAINT fk_category_parent FOREIGN KEY (tenant_id, parent_category_id)
        REFERENCES question_categories (tenant_id, category_id)
);
CREATE UNIQUE INDEX ux_category_sibling_name ON question_categories
    (tenant_id, COALESCE(parent_category_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name));
CREATE INDEX idx_category_path ON question_categories (tenant_id, path);

CREATE TABLE questions (
    question_id      UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    bank_id          UUID        NOT NULL,
    category_id      UUID,
    question_type    VARCHAR(30) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    latest_version   INTEGER     NOT NULL DEFAULT 0,
    approved_version INTEGER,
    row_version      BIGINT      NOT NULL DEFAULT 0,
    created_by       VARCHAR(100),
    created_on       TIMESTAMPTZ,
    updated_by       VARCHAR(100),
    updated_on       TIMESTAMPTZ,
    CONSTRAINT uk_question_tenant_id UNIQUE (tenant_id, question_id),
    CONSTRAINT fk_question_bank FOREIGN KEY (tenant_id, bank_id)
        REFERENCES question_banks (tenant_id, bank_id) ON DELETE CASCADE,
    CONSTRAINT fk_question_category FOREIGN KEY (tenant_id, category_id)
        REFERENCES question_categories (tenant_id, category_id)
);
CREATE INDEX idx_questions_bank ON questions (tenant_id, bank_id, status);
CREATE INDEX idx_questions_category ON questions (tenant_id, category_id, status);

CREATE TABLE question_versions (
    version_id     UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    question_id    UUID         NOT NULL,
    version_no     INTEGER      NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    content        JSONB        NOT NULL,
    answer_key     JSONB,
    explanation    TEXT,
    default_marks  NUMERIC(8, 2) NOT NULL DEFAULT 1 CHECK (default_marks >= 0),
    negative_marks NUMERIC(8, 2) NOT NULL DEFAULT 0 CHECK (negative_marks >= 0),
    difficulty     VARCHAR(20),
    tags           JSONB        NOT NULL DEFAULT '[]',
    search_vector  TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', COALESCE(content ->> 'text', ''))) STORED,
    authored_by    UUID         NOT NULL,
    created_on     TIMESTAMPTZ  NOT NULL,
    submitted_at   TIMESTAMPTZ,
    reviewed_by    UUID,
    reviewed_at    TIMESTAMPTZ,
    CONSTRAINT uk_question_version UNIQUE (tenant_id, question_id, version_no),
    CONSTRAINT fk_version_question FOREIGN KEY (tenant_id, question_id)
        REFERENCES questions (tenant_id, question_id) ON DELETE CASCADE
);
CREATE INDEX idx_question_versions_search ON question_versions USING GIN (search_vector);
CREATE INDEX idx_question_versions_tags ON question_versions USING GIN (tags);

CREATE TABLE question_reviews (
    review_id   UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL,
    question_id UUID          NOT NULL,
    version_no  INTEGER       NOT NULL,
    reviewer_id UUID          NOT NULL,
    decision    VARCHAR(20)   NOT NULL,
    comments    VARCHAR(2000),
    reviewed_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_review_version FOREIGN KEY (tenant_id, question_id, version_no)
        REFERENCES question_versions (tenant_id, question_id, version_no) ON DELETE CASCADE
);
CREATE INDEX idx_question_reviews ON question_reviews (tenant_id, question_id, version_no);

-- Only drafts are editable; once submitted, a version's content is frozen and its status only moves forward.
CREATE FUNCTION enforce_question_version_immutable() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status <> 'DRAFT' AND (NEW.content IS DISTINCT FROM OLD.content
        OR NEW.answer_key IS DISTINCT FROM OLD.answer_key OR NEW.explanation IS DISTINCT FROM OLD.explanation
        OR NEW.default_marks IS DISTINCT FROM OLD.default_marks
        OR NEW.negative_marks IS DISTINCT FROM OLD.negative_marks
        OR NEW.difficulty IS DISTINCT FROM OLD.difficulty OR NEW.tags IS DISTINCT FROM OLD.tags
        OR NEW.version_no IS DISTINCT FROM OLD.version_no OR NEW.question_id IS DISTINCT FROM OLD.question_id) THEN
        RAISE EXCEPTION 'Question version % is % and cannot be modified', OLD.version_no, OLD.status
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'IN_REVIEW')
        OR (OLD.status = 'IN_REVIEW' AND NEW.status IN ('APPROVED', 'REJECTED'))
        OR (OLD.status = 'APPROVED' AND NEW.status = 'RETIRED')) THEN
        RAISE EXCEPTION 'Invalid question version transition % -> %', OLD.status, NEW.status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_question_version_immutable BEFORE UPDATE ON question_versions
    FOR EACH ROW EXECUTE FUNCTION enforce_question_version_immutable();

DO
$$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY ['question_banks', 'question_categories', 'questions', 'question_versions', 'question_reviews']
        LOOP
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (telt_rls_bypass() OR tenant_id = telt_current_tenant()) '
                               || 'WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant())', t);
        END LOOP;
    IF '${app_role}' <> current_user AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
        EXECUTE format('REVOKE DELETE ON question_versions, question_reviews FROM %I', '${app_role}');
    END IF;
END;
$$;

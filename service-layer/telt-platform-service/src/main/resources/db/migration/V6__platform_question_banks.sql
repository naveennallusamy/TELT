-- Phase 2b: platform-owned question banks (same tables, reserved PLATFORM tenant), frozen releases and tenant grants.

-- Super admins author platform content in this tenant. Nobody can be a member of it.
INSERT INTO tenant (tenant_id, tenant_name, description, sub_admin_default_access_mode, separation_of_duties,
                    created_by, created_on)
VALUES ('00000000-0000-0000-0000-000000000001', '__platform__', 'Platform-owned content', 'TENANT_WIDE', FALSE,
        'SYSTEM', now());

CREATE FUNCTION telt_platform_tenant() RETURNS uuid LANGUAGE sql IMMUTABLE AS
$$ SELECT '00000000-0000-0000-0000-000000000001'::uuid $$;

ALTER TABLE tenant_memberships
    ADD CONSTRAINT ck_membership_not_platform CHECK (tenant_id <> '00000000-0000-0000-0000-000000000001');

-- Lineage of tenant questions cloned from a platform question.
ALTER TABLE questions
    ADD COLUMN source_question_id UUID,
    ADD COLUMN source_version_no  INTEGER,
    ADD COLUMN source_grant_id    UUID;

-- A release freezes the approved version of every active question in a platform bank at publish time.
CREATE TABLE bank_releases (
    release_id   UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'
        CHECK (tenant_id = '00000000-0000-0000-0000-000000000001'),
    bank_id      UUID          NOT NULL,
    release_no   INTEGER       NOT NULL,
    notes        VARCHAR(1000),
    item_count   INTEGER       NOT NULL,
    published_by UUID          NOT NULL,
    published_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_release_no UNIQUE (bank_id, release_no),
    CONSTRAINT uk_release_bank UNIQUE (bank_id, release_id),
    CONSTRAINT fk_release_bank FOREIGN KEY (tenant_id, bank_id) REFERENCES question_banks (tenant_id, bank_id)
);

CREATE TABLE bank_release_items (
    item_id     UUID PRIMARY KEY,
    release_id  UUID    NOT NULL REFERENCES bank_releases (release_id),
    tenant_id   UUID    NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'
        CHECK (tenant_id = '00000000-0000-0000-0000-000000000001'),
    question_id UUID    NOT NULL,
    version_no  INTEGER NOT NULL,
    CONSTRAINT uk_release_item UNIQUE (release_id, question_id),
    CONSTRAINT fk_release_item_version FOREIGN KEY (tenant_id, question_id, version_no)
        REFERENCES question_versions (tenant_id, question_id, version_no)
);
CREATE INDEX idx_release_items_question ON bank_release_items (question_id, version_no);

-- tenant_id is the grantee. bank_tenant_id pins the bank to the platform tenant.
CREATE TABLE platform_bank_grants (
    grant_id            UUID PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenant (tenant_id) ON DELETE CASCADE
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000001'),
    bank_tenant_id      UUID        NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'
        CHECK (bank_tenant_id = '00000000-0000-0000-0000-000000000001'),
    bank_id             UUID        NOT NULL,
    release_policy      VARCHAR(20) NOT NULL CHECK (release_policy IN ('PINNED', 'LATEST')),
    release_id          UUID,
    allow_clone         BOOLEAN     NOT NULL DEFAULT FALSE,
    answer_keys_visible BOOLEAN     NOT NULL DEFAULT FALSE,
    valid_from          TIMESTAMPTZ,
    valid_until         TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    revoked_at          TIMESTAMPTZ,
    created_by          VARCHAR(100),
    created_on          TIMESTAMPTZ,
    updated_by          VARCHAR(100),
    updated_on          TIMESTAMPTZ,
    CONSTRAINT fk_grant_bank FOREIGN KEY (bank_tenant_id, bank_id) REFERENCES question_banks (tenant_id, bank_id),
    CONSTRAINT fk_grant_release FOREIGN KEY (bank_id, release_id) REFERENCES bank_releases (bank_id, release_id),
    CONSTRAINT ck_grant_pinned_release CHECK ((release_policy = 'PINNED') = (release_id IS NOT NULL)),
    -- A clone carries the answer key, so cloning is only allowed when keys are visible.
    CONSTRAINT ck_grant_clone_needs_keys CHECK (NOT allow_clone OR answer_keys_visible),
    CONSTRAINT ck_grant_validity CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_until > valid_from)
);
CREATE UNIQUE INDEX ux_grant_active ON platform_bank_grants (tenant_id, bank_id) WHERE status = 'ACTIVE';

CREATE FUNCTION enforce_release_immutable() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Bank releases are immutable' USING ERRCODE = 'check_violation';
END;
$$;
CREATE TRIGGER trg_release_immutable BEFORE UPDATE ON bank_releases
    FOR EACH ROW EXECUTE FUNCTION enforce_release_immutable();
CREATE TRIGGER trg_release_item_immutable BEFORE UPDATE ON bank_release_items
    FOR EACH ROW EXECUTE FUNCTION enforce_release_immutable();

-- Releases the current tenant may read: the pinned release, or the newest one for LATEST grants.
-- SECURITY DEFINER so the policies below don't recurse through each other's row-level security.
CREATE FUNCTION telt_granted_releases() RETURNS SETOF uuid LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp AS
$$
SELECT CASE WHEN g.release_policy = 'PINNED' THEN g.release_id
            ELSE (SELECT r.release_id FROM bank_releases r WHERE r.bank_id = g.bank_id
                  ORDER BY r.release_no DESC LIMIT 1) END
FROM platform_bank_grants g
WHERE g.tenant_id = telt_current_tenant() AND g.status = 'ACTIVE'
  AND (g.valid_from IS NULL OR g.valid_from <= now()) AND (g.valid_until IS NULL OR g.valid_until > now())
$$;

CREATE FUNCTION telt_granted_bank(bank uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp AS
$$
SELECT EXISTS (SELECT 1 FROM bank_releases r WHERE r.bank_id = bank AND r.release_id IN (SELECT telt_granted_releases()))
$$;

CREATE FUNCTION telt_granted_version(question uuid, version integer) RETURNS boolean LANGUAGE sql STABLE
    SECURITY DEFINER SET search_path = public, pg_temp AS
$$
SELECT EXISTS (SELECT 1 FROM bank_release_items i WHERE i.question_id = question
                                                    AND (version IS NULL OR i.version_no = version)
                                                    AND i.release_id IN (SELECT telt_granted_releases()))
$$;

-- Shared read of platform content by grantee tenants. These are SELECT-only policies, so the existing
-- tenant_isolation policy still blocks any insert, update or delete of platform rows by a tenant.
ALTER TABLE bank_releases ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON bank_releases USING (telt_rls_bypass()) WITH CHECK (telt_rls_bypass());
CREATE POLICY granted_read ON bank_releases FOR SELECT USING (release_id IN (SELECT telt_granted_releases()));

ALTER TABLE bank_release_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON bank_release_items USING (telt_rls_bypass()) WITH CHECK (telt_rls_bypass());
CREATE POLICY granted_read ON bank_release_items FOR SELECT USING (release_id IN (SELECT telt_granted_releases()));

ALTER TABLE platform_bank_grants ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON platform_bank_grants
    USING (telt_rls_bypass() OR tenant_id = telt_current_tenant())
    WITH CHECK (telt_rls_bypass());

CREATE POLICY granted_read ON question_banks FOR SELECT
    USING (tenant_id = telt_platform_tenant() AND telt_granted_bank(bank_id));
CREATE POLICY granted_read ON questions FOR SELECT
    USING (tenant_id = telt_platform_tenant() AND telt_granted_version(question_id, NULL));
CREATE POLICY granted_read ON question_versions FOR SELECT
    USING (tenant_id = telt_platform_tenant() AND telt_granted_version(question_id, version_no));
CREATE POLICY platform_read ON question_categories FOR SELECT
    USING (tenant_id = telt_platform_tenant() AND telt_current_tenant() IS NOT NULL);

DO
$$
BEGIN
    IF '${app_role}' <> current_user AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
        EXECUTE format('REVOKE UPDATE, DELETE ON bank_releases, bank_release_items FROM %I', '${app_role}');
        EXECUTE format('REVOKE DELETE ON platform_bank_grants FROM %I', '${app_role}');
    END IF;
END;
$$;

-- Phase 1b: PostgreSQL row-level security on tenant-owned tables and NOTIFY-based authorization cache invalidation.
-- The application connects as ${app_role}, which must not own these tables (owners and superusers bypass RLS).
-- RlsJpaTransactionManager sets telt.tenant_id / telt.user_id / telt.bypass_rls per transaction.

CREATE FUNCTION telt_current_tenant() RETURNS uuid LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('telt.tenant_id', true), '')::uuid $$;

CREATE FUNCTION telt_current_user() RETURNS uuid LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('telt.user_id', true), '')::uuid $$;

CREATE FUNCTION telt_rls_bypass() RETURNS boolean LANGUAGE sql STABLE AS
$$ SELECT COALESCE(current_setting('telt.bypass_rls', true), '') = 'on' $$;

ALTER TABLE departments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON departments
    USING (telt_rls_bypass() OR tenant_id = telt_current_tenant())
    WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant());

-- A user may also read their own memberships in other tenants (tenant switcher).
ALTER TABLE tenant_memberships ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_memberships
    USING (telt_rls_bypass() OR tenant_id = telt_current_tenant() OR user_id = telt_current_user())
    WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant());

ALTER TABLE role_assignments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON role_assignments
    USING (telt_rls_bypass() OR tenant_id = telt_current_tenant())
    WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant());

ALTER TABLE organization_dtls ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organization_dtls
    USING (telt_rls_bypass() OR tenant_id = telt_current_tenant())
    WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant());

-- Platform-level events (tenant_id NULL) are only visible with bypass.
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_events
    USING (telt_rls_bypass() OR tenant_id = telt_current_tenant())
    WITH CHECK (telt_rls_bypass() OR tenant_id = telt_current_tenant());

-- The single-tenant-student rule must see memberships of every tenant, so it runs as the table owner.
ALTER FUNCTION enforce_single_tenant_student() SECURITY DEFINER SET search_path = public, pg_temp;

-- Authorization cache invalidation: payload '<tenant>:<user>' or '<tenant>:*'. Delivered on commit.
CREATE FUNCTION telt_notify_authz() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
    r RECORD;
BEGIN
    IF TG_OP = 'DELETE' THEN r := OLD; ELSE r := NEW; END IF;
    IF TG_TABLE_NAME IN ('tenant', 'departments') THEN
        PERFORM pg_notify('telt_authz', r.tenant_id::text || ':*');
    ELSE
        PERFORM pg_notify('telt_authz', r.tenant_id::text || ':' || r.user_id::text);
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_notify_authz AFTER INSERT OR UPDATE OR DELETE ON tenant_memberships
    FOR EACH ROW EXECUTE FUNCTION telt_notify_authz();
CREATE TRIGGER trg_notify_authz AFTER INSERT OR UPDATE OR DELETE ON role_assignments
    FOR EACH ROW EXECUTE FUNCTION telt_notify_authz();
CREATE TRIGGER trg_notify_authz AFTER INSERT OR UPDATE OR DELETE ON departments
    FOR EACH ROW EXECUTE FUNCTION telt_notify_authz();
CREATE TRIGGER trg_notify_authz AFTER UPDATE ON tenant
    FOR EACH ROW EXECUTE FUNCTION telt_notify_authz();

-- Least-privilege grants for the application role when it is a separate role from the migration user.
DO
$$
DECLARE
    app_role TEXT := '${app_role}';
    schema_name TEXT := current_schema();
BEGIN
    IF app_role <> current_user AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', schema_name, app_role);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO %I', schema_name, app_role);
        EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO %I', schema_name, app_role);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
                       schema_name, app_role);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT USAGE, SELECT ON SEQUENCES TO %I',
                       schema_name, app_role);
        EXECUTE format('REVOKE UPDATE, DELETE ON audit_events FROM %I', app_role);
        EXECUTE format('REVOKE ALL ON %I FROM %I', '${flyway:table}', app_role);
    END IF;
END;
$$;

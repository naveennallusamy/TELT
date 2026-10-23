-- Baseline schema for the TELT platform service (replaces Hibernate ddl-auto=update).

CREATE TABLE tenant (
    tenant_id   UUID PRIMARY KEY,
    tenant_name VARCHAR(200) NOT NULL UNIQUE,
    description VARCHAR(1000),
    created_by  VARCHAR(100),
    created_on  TIMESTAMPTZ,
    updated_by  VARCHAR(100),
    updated_on  TIMESTAMPTZ
);

CREATE TABLE roles (
    role_id    UUID PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    created_by VARCHAR(100),
    created_on TIMESTAMPTZ,
    updated_by VARCHAR(100),
    updated_on TIMESTAMPTZ
);

CREATE TABLE users (
    id                       UUID PRIMARY KEY,
    username                 VARCHAR(100) UNIQUE,
    email                    VARCHAR(320) NOT NULL UNIQUE,
    mobile_number            BIGINT       NOT NULL UNIQUE,
    password                 VARCHAR(100) NOT NULL,
    role_id                  UUID         NOT NULL REFERENCES roles (role_id),
    tenant_id                UUID REFERENCES tenant (tenant_id),
    country_code             VARCHAR(10),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts    INTEGER      NOT NULL DEFAULT 0,
    locked_until             TIMESTAMPTZ,
    password_change_required BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at      TIMESTAMPTZ,
    last_login_at            TIMESTAMPTZ,
    created_by               VARCHAR(100),
    created_on               TIMESTAMPTZ,
    updated_by               VARCHAR(100),
    updated_on               TIMESTAMPTZ,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX idx_users_tenant ON users (tenant_id);

CREATE TABLE user_personal_info (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    title         VARCHAR(20),
    first_name    VARCHAR(100),
    middle_name   VARCHAR(100),
    last_name     VARCHAR(100),
    gender        VARCHAR(20),
    date_of_birth DATE,
    nationality   VARCHAR(100),
    designation   VARCHAR(100),
    created_by    VARCHAR(100),
    created_on    TIMESTAMPTZ,
    updated_by    VARCHAR(100),
    updated_on    TIMESTAMPTZ
);

CREATE TABLE address_info (
    address_id    UUID PRIMARY KEY,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city          VARCHAR(100),
    state         VARCHAR(100),
    country       VARCHAR(100),
    zip_code      VARCHAR(20),
    created_by    VARCHAR(100),
    created_on    TIMESTAMPTZ,
    updated_by    VARCHAR(100),
    updated_on    TIMESTAMPTZ,
    CONSTRAINT uk_address_info UNIQUE (address_line1, address_line2, city, state, country, zip_code)
);

CREATE TABLE user_address_assoc (
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    address_id   UUID        NOT NULL REFERENCES address_info (address_id),
    address_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, address_id, address_type)
);

CREATE TABLE organization_dtls (
    id                UUID PRIMARY KEY,
    organization_name VARCHAR(200) NOT NULL,
    website           VARCHAR(255),
    office_phone      BIGINT,
    tenant_id         UUID REFERENCES tenant (tenant_id),
    created_by        VARCHAR(100),
    created_on        TIMESTAMPTZ,
    updated_by        VARCHAR(100),
    updated_on        TIMESTAMPTZ
);
CREATE INDEX idx_organization_tenant ON organization_dtls (tenant_id);

CREATE TABLE org_address_assoc (
    org_id       UUID        NOT NULL REFERENCES organization_dtls (id) ON DELETE CASCADE,
    address_id   UUID        NOT NULL REFERENCES address_info (address_id),
    address_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (org_id, address_id, address_type)
);

CREATE TABLE reference_values (
    id              UUID PRIMARY KEY,
    reference_type  VARCHAR(100),
    reference_value VARCHAR(255),
    status          VARCHAR(20) DEFAULT 'Active',
    created_by      VARCHAR(100),
    created_on      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    updated_on      TIMESTAMPTZ,
    CONSTRAINT uk_reference_values UNIQUE (reference_type, reference_value)
);

CREATE TABLE ref_course_details (
    id          UUID PRIMARY KEY,
    grad_type   VARCHAR(100),
    course_type VARCHAR(100),
    course_name VARCHAR(255),
    status      VARCHAR(20) DEFAULT 'Active',
    created_by  VARCHAR(100),
    created_on  TIMESTAMPTZ,
    updated_by  VARCHAR(100),
    updated_on  TIMESTAMPTZ,
    CONSTRAINT uk_ref_course_details UNIQUE (grad_type, course_type, course_name)
);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

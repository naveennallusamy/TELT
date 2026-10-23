INSERT INTO roles (role_id, name, created_by, created_on)
VALUES (gen_random_uuid(), 'SUPER_ADMIN', 'SYSTEM', now()),
       (gen_random_uuid(), 'TENANT_ADMIN', 'SYSTEM', now()),
       (gen_random_uuid(), 'USER', 'SYSTEM', now());

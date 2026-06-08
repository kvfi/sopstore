-- LOCAL DEV ONLY — do NOT apply to production.
--
-- Seeds an "anonymous" all-zeros tenant plus an admin login so that local
-- form login works. The user lives under the all-zeros tenant on purpose:
-- form login runs before any tenant context exists, so Hibernate's @TenantId
-- discriminator (and the RLS current_tenant_id() default) resolve to
-- 00000000-0000-0000-0000-000000000000. A user under any other tenant would
-- not be found by AppUserDetailsService.findByEmail at authentication time.
--
-- Credentials: admin@example.com / admin   (Argon2id hash below)
-- Re-runnable: safe to apply repeatedly (and after a schema reset).

INSERT INTO tenant (id, name, slug, status)
VALUES ('00000000-0000-0000-0000-000000000000', 'Dev Tenant', 'dev', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, tenant_id, email, display_name, password_hash, status)
VALUES (
  '11111111-1111-1111-1111-111111111111',
  '00000000-0000-0000-0000-000000000000',
  'admin@example.com',
  'Dev Admin',
  '$argon2id$v=19$m=16384,t=2,p=1$JH0lQwTkU4BBSOz/B2CMTw$BK46oxZoPzXP1kgC0oO9BfTI7+GwdFi49oyiKRk33Xw',
  'ACTIVE'
)
ON CONFLICT (tenant_id, email) DO UPDATE
  SET password_hash = EXCLUDED.password_hash,
      status        = 'ACTIVE';

INSERT INTO user_role (user_id, role)
SELECT '11111111-1111-1111-1111-111111111111', r
FROM (VALUES
        ('SUPER_ADMIN'), ('TENANT_ADMIN'), ('QUALITY_MANAGER'), ('AUTHOR'),
        ('REVIEWER'), ('APPROVER'), ('TRAINER'), ('AUDITOR'), ('VIEWER'),
        ('PLATFORM_OPS')
     ) AS x(r)
ON CONFLICT (user_id, role) DO NOTHING;

-- Default prerequisite-type catalogue for the dev tenant (manage via Settings).
INSERT INTO prerequisite_type (id, tenant_id, name)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000000', n
FROM (VALUES
        ('Equipment / tool'), ('Material / consumable'), ('Document / reference'),
        ('Training / qualification'), ('Safety / PPE'), ('System access'),
        ('Approval / sign-off')
     ) AS t(n)
ON CONFLICT (tenant_id, name) DO NOTHING;

-- A few reusable library prerequisites for the dev tenant (manage via Settings).
INSERT INTO prerequisite (id, tenant_id, type, label)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000000', d.type, d.label
FROM (VALUES
        ('Equipment / tool', 'Calibrated torque wrench'),
        ('Document / reference', 'SOP-0011 Gowning procedure'),
        ('Training / qualification', 'Aseptic processing qualification (within 12 months)'),
        ('Safety / PPE', 'Sterile gown, gloves, and mask')
     ) AS d(type, label)
ON CONFLICT (tenant_id, type, label) DO NOTHING;

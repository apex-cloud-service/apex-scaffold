-- V90: 回填 platform-full 的能力订阅集合（能力运营 D4）
-- 与 MySQL 版本语义一致：仅对空 features/NULL 的平台全量套餐显式授予当前已登记能力，
-- 不覆盖运营人员已配置的非空能力集合。
UPDATE sys_tenant_package
SET features = '[
        "identity-auth",
        "user-org",
        "workflow",
        "file-storage",
        "office-message",
        "tenant-governance",
        "integration-connector",
        "iot-device"
    ]'::jsonb,
    version = version + 1,
    update_time = NOW()
WHERE package_code = 'platform-full'
  AND deleted = 0
  AND (features IS NULL OR features = '[]'::jsonb);

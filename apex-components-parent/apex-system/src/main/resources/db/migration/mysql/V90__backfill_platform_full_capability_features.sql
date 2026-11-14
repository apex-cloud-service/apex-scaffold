-- V90: 回填 platform-full 的能力订阅集合（能力运营 D4）
-- V78 创建 platform-full 时 features 初始化为空数组；在“空集合 = 未订阅任何能力”
-- 的收紧语义下，必须显式回填当前已登记能力，避免内置全量套餐调用开放能力时被误拒。
-- 仅回填空数组/NULL，且条件包含 deleted = 0；不覆盖运营人员已配置的非空能力集合。
-- 后续新增能力仍由套餐配置显式授予，不在本迁移中动态读取代码目录。

UPDATE `sys_tenant_package`
SET `features` = CAST(JSON_ARRAY(
        'identity-auth',
        'user-org',
        'workflow',
        'file-storage',
        'office-message',
        'tenant-governance',
        'integration-connector',
        'iot-device'
    ) AS JSON),
    `version` = `version` + 1,
    `update_time` = NOW()
WHERE `package_code` = 'platform-full'
  AND `deleted` = 0
  AND (`features` IS NULL OR JSON_LENGTH(`features`) = 0);

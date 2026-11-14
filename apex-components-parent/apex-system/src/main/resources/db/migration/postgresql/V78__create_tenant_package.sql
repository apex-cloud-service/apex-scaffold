-- V78: 租户套餐与功能裁剪（统一认证收口 / 方案批次 B3）
-- 语义与 MySQL 版本一致：平台级套餐定义表 + platform-full 数据驱动种子。

CREATE TABLE IF NOT EXISTS sys_tenant_package (
    id               BIGINT       NOT NULL PRIMARY KEY,
    package_code     VARCHAR(64)  NOT NULL,
    package_name     VARCHAR(128) NOT NULL,
    menu_ids         JSONB        DEFAULT NULL,
    permission_codes JSONB        DEFAULT NULL,
    features         JSONB        DEFAULT NULL,
    status           INT          NOT NULL DEFAULT 1,
    remark           VARCHAR(500) DEFAULT NULL,
    create_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    version          INT          NOT NULL DEFAULT 0,
    deleted          INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_package_code UNIQUE (package_code)
);

COMMENT ON TABLE sys_tenant_package IS '租户套餐定义表（平台级，菜单/权限/能力裁剪）';

-- platform-full 种子：全量菜单与权限码（数据驱动聚合）
INSERT INTO sys_tenant_package
    (id, package_code, package_name, menu_ids, permission_codes, features, status, remark, create_time, update_time)
SELECT 1, 'platform-full', '平台全量套餐',
       COALESCE((SELECT jsonb_agg(id) FROM sys_menu WHERE deleted = 0), '[]'::jsonb),
       COALESCE((SELECT jsonb_agg(code) FROM sys_permission WHERE deleted = 0), '[]'::jsonb),
       '[]'::jsonb,
       1, '内置全量套餐：不裁剪任何菜单与权限码', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant_package WHERE package_code = 'platform-full' AND deleted = 0);

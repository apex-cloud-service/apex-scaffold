-- ============================================================
-- V26: Add Keycloak integration fields to sys_user
-- ============================================================
-- Description: Add Keycloak SSO integration support fields
-- Author: Apex Team
-- Date: 2026-09-08
-- ============================================================

-- Add keycloak_sub field to sys_user table
ALTER TABLE sys_user
ADD COLUMN keycloak_sub VARCHAR(255);

COMMENT ON COLUMN sys_user.keycloak_sub IS 'Keycloak用户唯一标识(sub claim)';

-- Add unique constraint for keycloak_sub
ALTER TABLE sys_user
ADD CONSTRAINT uk_keycloak_sub UNIQUE (keycloak_sub);

-- Add email field if not exists (for Keycloak OIDC user info)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = current_schema() 
        AND table_name = 'sys_user' 
        AND column_name = 'email'
    ) THEN
        ALTER TABLE sys_user ADD COLUMN email VARCHAR(255);
        COMMENT ON COLUMN sys_user.email IS '用户邮箱';
    END IF;
END $$;

-- Add index for email if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE schemaname = current_schema() 
        AND tablename = 'sys_user' 
        AND indexname = 'idx_email'
    ) THEN
        CREATE INDEX idx_email ON sys_user (email);
    END IF;
END $$;

-- Add comment for sys_user table
COMMENT ON TABLE sys_user IS '系统用户表（支持本地JWT认证和Keycloak SSO认证）';

-- ============================================================
-- V37 - Add email and keycloak_sub fields to sys_user table
-- ============================================================
-- Purpose: Support Keycloak OIDC login integration
-- - email: User email address from Keycloak ID Token
-- - keycloak_sub: Keycloak subject identifier (unique user ID)
--
-- Corresponding MySQL version: V37__add_email_and_keycloak_sub_to_sys_user.sql
-- Date: 2026-09-17
-- Author: Apex Team
-- ============================================================

-- Add email field after phone
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS email VARCHAR(100);

COMMENT ON COLUMN sys_user.email IS '用户邮箱';

-- Add keycloak_sub field after email
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS keycloak_sub VARCHAR(100);

COMMENT ON COLUMN sys_user.keycloak_sub IS 'Keycloak用户唯一标识';

-- Create unique index on keycloak_sub (PostgreSQL syntax)
CREATE UNIQUE INDEX IF NOT EXISTS uk_keycloak_sub ON sys_user (keycloak_sub);

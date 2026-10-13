import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

BASE64_KEYS = ("APEX_DATA_ENCRYPTION_KEY", "SECURITY_ENCRYPTION_KEY", "APEX_CONFIG_ENCRYPTION_KEY")
FIELDS = ("secret", "backup_codes", "used_backup_codes")


def decode_key(value):
    try:
        key = base64.b64decode(value, validate=True)
        if len(key) != 32:
            raise ValueError()
        return key
    except (ValueError, TypeError):
        raise ValueError("需要 Base64 编码的 32 字节密钥") from None


def validate_keys(env):
    for name in BASE64_KEYS:
        try:
            decode_key(env.get(name, ""))
        except ValueError:
            raise ValueError(name + ": 需要 Base64 编码的 32 字节密钥") from None
    value = env.get("CONFIG_ENCRYPTION_KEY", "")
    if not value.strip() or len(value.encode("utf-8")) != 32:
        raise ValueError("CONFIG_ENCRYPTION_KEY: 需要非空白的 32 字节 UTF-8 字符串")
    jwt = env.get("JWT_SECRET", "")
    if len(jwt.encode("utf-8")) < 32 or any(
        marker in jwt.lower() for marker in ("change-me", "change_me", "please-change", "changeme")
    ):
        raise ValueError("JWT_SECRET: 需要至少 32 字节且不能使用模板占位值")


def aes(key):
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError:
        raise ValueError("离线迁移需要 cryptography；请安装 scripts/utils/local-deployment-requirements.txt") from None
    return AESGCM(key)


def encrypt(value, key):
    iv = os.urandom(12)
    return base64.b64encode(iv + aes(key).encrypt(iv, value.encode("utf-8"), None)).decode("ascii")


def decrypt(value, key):
    try:
        raw = base64.b64decode(value, validate=True)
        if len(raw) < 28:
            raise ValueError()
        return aes(key).decrypt(raw[:12], raw[12:], None).decode("utf-8")
    except Exception:
        raise ValueError("密文格式或认证校验失败") from None


def valid_plaintext(field, value):
    if not isinstance(value, str):
        return False
    if field == "secret":
        return re.fullmatch(r"[A-Z2-7]{32}", value) is not None
    return value == "" or re.fullmatch(r"[0-9]{8}(,[0-9]{8})*", value) is not None


def transform(field, value, key, old_key):
    if value is None and field != "secret":
        return None
    if not isinstance(value, str):
        raise ValueError("TOTP 字段类型无效")
    for candidate_key in (key, old_key):
        if candidate_key is None:
            continue
        try:
            plain = decrypt(value, candidate_key)
        except ValueError:
            continue
        if not valid_plaintext(field, plain):
            raise ValueError("解密后的 TOTP 字段格式无效")
        return value if candidate_key == key else encrypt(plain, key)
    if not valid_plaintext(field, value):
        raise ValueError("未知 TOTP 格式或密钥不匹配；拒绝迁移")
    return encrypt(value, key)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def private_write(path, data):
    with path.open("xb") as stream:
        os.chmod(path, 0o600)
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def prepare(source, output, key, old_key=None):
    if output.exists():
        raise FileExistsError("输出目录已存在；拒绝覆盖")
    original = source.read_bytes()
    rows = json.loads(original)
    if not isinstance(rows, list):
        raise ValueError("输入必须是包含完整 auth_totp 行的 JSON 数组")
    identities = set()
    changed_fields = 0
    changed_rows = 0
    for row in rows:
        if not isinstance(row, dict) or any(name not in row for name in (*FIELDS, "id", "tenant_id", "version")):
            raise ValueError("行缺少迁移所需字段")
        if not isinstance(row["id"], int) or isinstance(row["id"], bool) or not isinstance(row["tenant_id"], str):
            raise ValueError("行身份字段无效")
        identity = (row["tenant_id"], row["id"])
        if identity in identities:
            raise ValueError("重复行身份；拒绝迁移")
        identities.add(identity)
        changed = False
        for field in FIELDS:
            transformed = transform(field, row[field], key, old_key)
            if transformed != row[field]:
                changed_fields += 1
                changed = True
            row[field] = transformed
        changed_rows += int(changed)
    candidate = (json.dumps(rows, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    report = {
        "format": "apex-totp-offline-v1",
        "cipher": "Base64(IV[12] + AES-256-GCM ciphertext + tag[16])",
        "rows": len(rows), "changed_rows": changed_rows, "changed_fields": changed_fields,
        "backup_sha256": digest(original), "candidate_sha256": digest(candidate),
        "database_applied": False,
        "apply_requirements": [
            "停写并另行备份数据库；在隔离副本验证后审查变更",
            "使用 tenant_id、id、version 及旧字段值执行事务内比较更新；冲突必须整批回滚",
            "只替换三个敏感字段；保持其他字段；恢复写入前验证 MFA",
            "回滚须在停写窗口使用原始备份；恢复后仍需原加密密钥",
        ],
    }
    output.mkdir(mode=0o700)
    private_write(output / "backup.json", original)
    private_write(output / "candidate.json", candidate)
    private_write(output / "review.json", (json.dumps(report, ensure_ascii=False, indent=2) + "\n").encode())
    return report


def rollback(bundle, output):
    report = json.loads((bundle / "review.json").read_bytes())
    backup = (bundle / "backup.json").read_bytes()
    candidate = (bundle / "candidate.json").read_bytes()
    if report.get("format") != "apex-totp-offline-v1" or digest(backup) != report.get("backup_sha256") or digest(candidate) != report.get("candidate_sha256"):
        raise ValueError("迁移包摘要校验失败；拒绝回滚")
    private_write(output, backup)


def compose_check(env_file):
    root = Path(__file__).resolve().parents[2]
    import yaml

    command = ["podman-compose", "-f", str(root / "podman-compose.yml")]
    if env_file:
        command += ["--env-file", str(env_file.resolve())]
    result = subprocess.run(command + ["config"], cwd=root, capture_output=True, text=True)
    if result.returncode:
        raise ValueError("Compose 配置验证失败；请检查必需环境变量与 podman-compose 版本（输出已隐藏）")
    config = yaml.safe_load(result.stdout)
    environments = [service.get("environment", {}) for service in config["services"].values()]
    secured = [environment for environment in environments if "APEX_DATA_ENCRYPTION_KEY" in environment]
    if not secured:
        raise ValueError("Compose 配置缺少应用安全环境变量")
    for environment in secured:
        validate_keys(environment)


def main():
    parser = argparse.ArgumentParser(description="密钥格式校验及离线 TOTP 迁移准备；不连接数据库，不输出密钥")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("validate", help="校验进程环境变量")
    compose = commands.add_parser("compose", help="捕获 Compose 渲染结果并校验注入的密钥；不启动服务")
    compose.add_argument("--env-file", type=Path)
    migration = commands.add_parser("prepare", help="完整 auth_totp JSON 快照 -> 私有迁移包")
    migration.add_argument("--input", required=True, type=Path)
    migration.add_argument("--output", required=True, type=Path)
    restore = commands.add_parser("rollback", help="校验迁移包并恢复离线快照；不修改数据库")
    restore.add_argument("--bundle", required=True, type=Path)
    restore.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if args.command == "validate":
            validate_keys(os.environ)
        elif args.command == "compose":
            compose_check(args.env_file)
        elif args.command == "prepare":
            key = decode_key(os.environ.get("APEX_DATA_ENCRYPTION_KEY", ""))
            old = os.environ.get("TOTP_OLD_DATA_ENCRYPTION_KEY")
            report = prepare(args.input, args.output, key, decode_key(old) if old is not None else None)
            print("rows={}, changed_rows={}, changed_fields={}".format(report["rows"], report["changed_rows"], report["changed_fields"]))
        else:
            rollback(args.bundle, args.output)
        print("验证完成；未连接或修改数据库")
        return 0
    except (ValueError, OSError, KeyError, TypeError):
        print("操作失败：请检查输入格式、密钥、依赖和输出路径；敏感诊断信息已隐藏", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

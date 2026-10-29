#!/bin/bash

# 文档一致性检查脚本
# 用于 CI 流程,验证文档与代码的一致性
# 
# 使用方式:
#   ./check-doc-consistency.sh
#
# 退出码:
#   0 - 所有检查通过
#   1 - 发现不一致问题

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

echo "========================================="
echo " 文档一致性检查"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

# 检查 1: 版本号一致性
echo "[1/5] 检查版本号一致性..."

# 从根 POM 提取项目版本号（跳过 parent 部分）
PROJECT_VERSION=$(awk '/<parent>/,/<\/parent>/{next} /<version>/{print; exit}' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
echo "  项目版本: ${PROJECT_VERSION}"

# 检查 README.md 中项目版本号徽章
README_PROJECT_VERSION=$(grep -m 1 'Apex_Scaffold-v' README.md | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | sed 's/v//')

if [ -z "${README_PROJECT_VERSION}" ]; then
  echo -e "${RED}  ✗ README.md 中未找到项目版本号徽章${NC}"
  ERRORS=$((ERRORS + 1))
elif [ "${README_PROJECT_VERSION}" != "${PROJECT_VERSION}" ]; then
  echo -e "${RED}  ✗ README.md 项目版本号 (${README_PROJECT_VERSION}) 与 POM (${PROJECT_VERSION}) 不一致${NC}"
  ERRORS=$((ERRORS + 1))
else
  echo -e "${GREEN}  ✓ 项目版本号一致: ${PROJECT_VERSION}${NC}"
fi

echo ""

# 检查 2: 环境变量文档一致性
echo "[2/5] 检查环境变量文档一致性..."

# 检查 .env.example 是否存在
if [ ! -f ".env.example" ]; then
  echo -e "${RED}  ✗ .env.example 文件不存在${NC}"
  ERRORS=$((ERRORS + 1))
else
  # 统计环境变量数量
  ENV_COUNT=$(grep -c '^[A-Z_]' .env.example || true)
  echo "  .env.example 中的环境变量数: ${ENV_COUNT}"
  
  if [ "${ENV_COUNT}" -lt 170 ]; then
    echo -e "${YELLOW}  ⚠ 环境变量数量偏少,建议检查是否遗漏${NC}"
    WARNINGS=$((WARNINGS + 1))
  else
    echo -e "${GREEN}  ✓ 环境变量文档完整${NC}"
  fi
fi

echo ""

# 检查 3: Handler 类的 Javadoc 完整性
echo "[3/5] 检查 Handler 类 Javadoc 完整性..."

HANDLER_FILES=$(find apex-system/src/main/java -name "*Handler.java" | grep -v "Base" | grep -v "Abstract")
MISSING_JAVADOC=0

for file in ${HANDLER_FILES}; do
  # 检查类级 Javadoc
  if ! grep -q "/\*\*" "${file}"; then
    echo -e "${YELLOW}  ⚠ ${file} 缺少 Javadoc${NC}"
    MISSING_JAVADOC=$((MISSING_JAVADOC + 1))
  fi
done

if [ "${MISSING_JAVADOC}" -eq 0 ]; then
  echo -e "${GREEN}  ✓ 所有 Handler 类都有 Javadoc${NC}"
else
  echo -e "${YELLOW}  ⚠ ${MISSING_JAVADOC} 个 Handler 类缺少 Javadoc${NC}"
  WARNINGS=$((WARNINGS + 1))
fi

echo ""

# 检查 4: 路由配置与文档一致性
echo "[4/5] 检查路由配置与文档一致性..."

# 检查是否存在路由文档
if [ -f "docs/03-api/API_ROUTES.md" ]; then
  echo -e "${GREEN}  ✓ 路由文档存在${NC}"
else
  echo -e "${YELLOW}  ⚠ 建议创建 docs/03-api/API_ROUTES.md 记录所有路由${NC}"
  WARNINGS=$((WARNINGS + 1))
fi

echo ""

# 检查 5: Flyway 迁移脚本命名规范
echo "[5/5] 检查 Flyway 迁移脚本命名规范..."

MIGRATION_FILES=$(find apex-system/src/main/resources/db/migration -name "V*.sql" 2>/dev/null || true)

if [ -z "${MIGRATION_FILES}" ]; then
  echo -e "${YELLOW}  ⚠ 未找到 Flyway 迁移脚本${NC}"
  WARNINGS=$((WARNINGS + 1))
else
  INVALID_NAMES=0
  for file in ${MIGRATION_FILES}; do
    basename=$(basename "${file}")
    # 检查格式: V数字__描述.sql (描述可包含大小写字母、数字、下划线)
    if ! echo "${basename}" | grep -qE '^V[0-9]+__[a-zA-Z0-9_]+\.sql$'; then
      echo -e "${YELLOW}  ⚠ ${basename} 命名不符合规范${NC}"
      INVALID_NAMES=$((INVALID_NAMES + 1))
    fi
  done
  
  if [ "${INVALID_NAMES}" -eq 0 ]; then
    echo -e "${GREEN}  ✓ 所有迁移脚本命名规范${NC}"
  else
    echo -e "${YELLOW}  ⚠ ${INVALID_NAMES} 个迁移脚本命名不规范${NC}"
    WARNINGS=$((WARNINGS + 1))
  fi
fi

echo ""
echo "========================================="
echo " 检查结果汇总"
echo "========================================="
echo ""
echo "  错误: ${ERRORS}"
echo "  警告: ${WARNINGS}"
echo ""

if [ "${ERRORS}" -gt 0 ]; then
  echo -e "${RED}✗ 文档一致性检查失败${NC}"
  exit 1
elif [ "${WARNINGS}" -gt 0 ]; then
  echo -e "${YELLOW}⚠ 文档一致性检查通过,但存在警告${NC}"
  exit 0
else
  echo -e "${GREEN}✓ 文档一致性检查全部通过${NC}"
  exit 0
fi

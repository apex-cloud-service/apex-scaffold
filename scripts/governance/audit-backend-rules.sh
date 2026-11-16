#!/usr/bin/env bash
# 后端编码红线扫描（apex-scaffold）
# 用法：bash scripts/governance/audit-backend-rules.sh [--strict]
# 仅报告，不改代码。--strict 时有命中则退出码 1。
set -u

STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# 只统计"真正生效的注解"，排除 Javadoc/注释中的同名字样
ANNO() { grep -rlE --include="*.java" "$1" . 2>/dev/null | grep -v "/target/" | sort -u; }

HITS=0
hit() { HITS=$((HITS + 1)); printf '  [HIT]  %s\n' "$1"; }
ok() { printf '  [OK]   %s\n' "$1"; }

echo "== 1. @Autowired（红线：必须构造器注入） =="
R=$(ANNO "^[[:space:]]*@(org\.springframework\.beans\.factory\.annotation\.)?Autowired")
if [ -n "$R" ]; then hit "@Autowired 命中文件：$(echo "$R" | tr '\n' ' ')"; else ok "无 @Autowired"; fi

echo "== 2. @Controller / @RestController（红线：只允许 Handler + RouterFunction） =="
R=$(ANNO "^[[:space:]]*@RestController\(|^[[:space:]]*@Controller\(")
if [ -n "$R" ]; then
  hit "注解式控制器命中：$(echo "$R" | tr '\n' ' ')"
else
  ok "无注解式控制器（注：Javadoc/注释中的同名字样不计）"
fi

echo "== 3. @RestControllerAdvice（例外清单：BE-004 评估后再决定是否下沉 ErrorWebExceptionHandler） =="
R=$(grep -rlE --include="*.java" "^[[:space:]]*@RestControllerAdvice" . 2>/dev/null | grep -v "/target/")
echo "  命中：$(echo "${R:-无}" | tr '\n' ' ')"
echo "  说明：异常处理器不改变接口契约，属已登记例外，不阻断。"

echo "== 4. 对象转换（红线：MapStruct，禁 BeanUtils / 手写逐行拷贝） =="
R=$(ANNO "BeanUtils\.copyProperties")
if [ -n "$R" ]; then hit "BeanUtils 拷贝：$(echo "$R" | tr '\n' ' ')"; else ok "无 BeanUtils.copyProperties"; fi

echo "== 5. 日志（红线：@Slf4j，禁手动 LoggerFactory） =="
R_MAIN=$(grep -rlE --include="*.java" "^[[:space:]]*private[[:space:]]+static[[:space:]]+final[[:space:]]+Logger" . 2>/dev/null | grep -v "/target/" | grep -v "/test/")
R_TEST=$(grep -rl "LoggerFactory.getLogger" --include="*.java" . 2>/dev/null | grep -v "/target/" | grep "/test/")
R="$R_MAIN"
if [ -n "$R" ]; then hit "生产代码手动 Logger：$(echo "$R" | tr '\n' ' ')"; else ok "生产代码无手动 Logger"; fi
echo "  测试代码手动 Logger：$(echo "${R_TEST:-无}" | tr '\n' ' ')（测试可保留，不计入阻断）"

echo "== 6. Lombok 覆盖抽查（POJO 手写 getter/setter） =="
HAND=$(grep -rl --include="*.java" -E "public (void set|String get|Integer get|Long get)" . 2>/dev/null | grep -v "/target/" | grep -v "/test/" | head -10)
if [ -n "$HAND" ]; then
  echo "  疑似手写存取方法（抽样前 10）："
  echo "$HAND" | sed 's/^/    /'
  echo "  说明：接口/枚举/Builder 等合法场景需人工确认，不阻断。"
else
  ok "未发现手写 getter/setter 抽样命中"
fi

echo "== 7. 构建环境 =="
if [ -f .sdkmanrc ]; then ok ".sdkmanrc 存在（sdk env 固定 JDK/mvnd）"; else hit "缺少 .sdkmanrc"; fi
if grep -q "spring-boot-starter-webflux" pom.xml 2>/dev/null; then ok "根 POM 声明 WebFlux"; else hit "根 POM 未见 WebFlux 依赖"; fi
if grep -q "spring-boot-starter-web<" pom.xml 2>/dev/null; then hit "根 POM 存在 MVC starter，需评估与 WebFlux 冲突"; else ok "根 POM 无 MVC starter"; fi

echo
if [ "$HITS" -eq 0 ]; then
  echo "✅ 后端红线扫描无阻断项"
  exit 0
else
  echo "⚠️ 命中 ${HITS} 类阻断项（默认仅警告）"
  [ "$STRICT" -eq 1 ] && exit 1
  exit 0
fi

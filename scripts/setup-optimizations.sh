#!/bin/bash

###############################################################################
# setup-optimizations.sh - 执行优化任务的环境检查与设置
# 用法：./scripts/setup-optimizations.sh  无参数；交互式执行
# 作者：Apex Team    创建：2026-10-01
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_ROOT="/Users/work/apex-admin-web"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_command() {
    if ! command -v "$1" &> /dev/null; then
        log_error "$1 未安装"
        return 1
    fi
    log_success "$1 已安装"
    return 0
}

check_environment() {
    log_info "检查环境..."
    
    local all_ok=true
    
    check_command "node" || all_ok=false
    check_command "npm" || all_ok=false
    check_command "mvnd" || all_ok=false
    check_command "java" || all_ok=false
    check_command "podman" || all_ok=false
    check_command "podman-compose" || all_ok=false
    
    if [ "$all_ok" = true ]; then
        log_success "环境检查通过"
        
        log_info "Node 版本: $(node --version)"
        log_info "npm 版本: $(npm --version)"
        log_info "Java 版本: $(java -version 2>&1 | head -n 1)"
        log_info "Maven Daemon 版本: $(mvnd --version | head -n 1)"
        log_info "Podman 版本: $(podman --version)"
        return 0
    else
        log_error "环境检查失败，请安装缺失的工具"
        return 1
    fi
}

setup_vitest() {
    log_info "======================================"
    log_info "1. 设置 Vitest 测试框架"
    log_info "======================================"
    
    if [ ! -d "$FRONTEND_ROOT" ]; then
        log_error "前端项目目录不存在: $FRONTEND_ROOT"
        return 1
    fi
    
    cd "$FRONTEND_ROOT"
    
    log_info "安装 Vitest 及相关依赖..."
    npm install -D vitest @vitest/ui @vue/test-utils happy-dom jsdom
    
    log_info "创建 Vitest 配置文件..."
    cat > vitest.config.ts << 'EOF'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: [
        'node_modules/',
        'src/test/',
        '**/*.spec.ts',
        '**/*.test.ts',
        'dist/',
        '*.config.ts'
      ]
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
EOF
    
    log_info "创建测试目录和设置文件..."
    mkdir -p src/test
    cat > src/test/setup.ts << 'EOF'
import { vi } from 'vitest'

global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}))

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})
EOF
    
    log_info "创建示例单元测试..."
    mkdir -p src/utils/__tests__
    cat > src/utils/__tests__/validate.spec.ts << 'EOF'
import { describe, it, expect } from 'vitest'
import { isValidEmail, isValidPhone } from '../validate'

describe('validate utils', () => {
  describe('isValidEmail', () => {
    it('should validate correct email', () => {
      expect(isValidEmail('test@example.com')).toBe(true)
    })

    it('should reject invalid email', () => {
      expect(isValidEmail('invalid-email')).toBe(false)
    })
  })

  describe('isValidPhone', () => {
    it('should validate correct phone', () => {
      expect(isValidPhone('13800138000')).toBe(true)
    })

    it('should reject invalid phone', () => {
      expect(isValidPhone('12345')).toBe(false)
    })
  })
})
EOF
    
    log_info "更新 package.json 脚本..."
    npm pkg set scripts.test="vitest"
    npm pkg set scripts.test:ui="vitest --ui"
    npm pkg set scripts.test:coverage="vitest --coverage"
    
    log_success "Vitest 配置完成"
}

setup_playwright() {
    log_info "======================================"
    log_info "2. 设置 Playwright E2E 测试"
    log_info "======================================"
    
    cd "$FRONTEND_ROOT"
    
    log_info "安装 Playwright..."
    npm install -D @playwright/test
    
    log_info "初始化 Playwright..."
    npx playwright install chromium firefox webkit
    
    log_info "创建 Playwright 配置..."
    cat > playwright.config.ts << 'EOF'
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
})
EOF
    
    log_info "创建示例 E2E 测试..."
    mkdir -p e2e
    cat > e2e/login.spec.ts << 'EOF'
import { test, expect } from '@playwright/test'

test.describe('Login Page', () => {
  test('should display login form', async ({ page }) => {
    await page.goto('/')
    
    await expect(page.locator('input[type="text"]')).toBeVisible()
    await expect(page.locator('input[type="password"]')).toBeVisible()
    await expect(page.locator('button[type="submit"]')).toBeVisible()
  })

  test('should show error on invalid credentials', async ({ page }) => {
    await page.goto('/')
    
    await page.fill('input[type="text"]', 'invalid@example.com')
    await page.fill('input[type="password"]', 'wrongpassword')
    await page.click('button[type="submit"]')
    
    await expect(page.locator('.el-message--error')).toBeVisible()
  })
})
EOF
    
    npm pkg set scripts.test:e2e="playwright test"
    npm pkg set scripts.test:e2e:ui="playwright test --ui"
    npm pkg set scripts.test:e2e:report="playwright show-report"
    
    log_success "Playwright E2E 测试配置完成"
}

setup_bundle_analysis() {
    log_info "======================================"
    log_info "3. 设置构建产物分析"
    log_info "======================================"
    
    cd "$FRONTEND_ROOT"
    
    log_info "安装 rollup-plugin-visualizer..."
    npm install -D rollup-plugin-visualizer
    
    log_info "更新 vite.config.ts..."
    cat > vite.config.ts.new << 'EOF'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { visualizer } from 'rollup-plugin-visualizer'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    visualizer({
      filename: './dist/stats.html',
      open: false,
      gzipSize: true,
      brotliSize: true,
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'echarts': ['echarts'],
        }
      }
    },
    chunkSizeWarningLimit: 1000,
  }
})
EOF
    
    if [ -f vite.config.ts ]; then
        mv vite.config.ts vite.config.ts.bak
    fi
    mv vite.config.ts.new vite.config.ts
    
    npm pkg set scripts.analyze="vite build && open dist/stats.html"
    
    log_success "构建产物分析配置完成"
}

setup_api_docs() {
    log_info "======================================"
    log_info "4. 设置 API 文档生成"
    log_info "======================================"
    
    cd "$PROJECT_ROOT"
    
    log_info "检查 SpringDoc 配置..."
    if grep -r "springdoc" apex-system/src/main/resources/application*.yml > /dev/null 2>&1; then
        log_success "SpringDoc 已配置"
    else
        log_warn "未找到 SpringDoc 配置"
    fi
    
    log_info "创建 API 文档导出脚本..."
    cat > scripts/export-api-docs.sh << 'EOFSCRIPT'
#!/bin/bash

set -e

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
OUTPUT_DIR="${OUTPUT_DIR:-./docs/api}"

mkdir -p "$OUTPUT_DIR"

echo "导出 OpenAPI 规范..."
curl -s "${API_BASE_URL}/v3/api-docs" > "${OUTPUT_DIR}/openapi.json"

echo "导出 OpenAPI YAML..."
curl -s "${API_BASE_URL}/v3/api-docs.yaml" > "${OUTPUT_DIR}/openapi.yaml"

echo "API 文档已导出到 $OUTPUT_DIR"
echo "在线文档: ${API_BASE_URL}/swagger-ui.html"
EOFSCRIPT
    
    chmod +x scripts/export-api-docs.sh
    
    log_info "安装 API 文档生成工具..."
    cd "$FRONTEND_ROOT"
    npm install -D @openapitools/openapi-generator-cli
    
    log_info "创建 OpenAPI Generator 配置..."
    cat > openapitools.json << 'EOF'
{
  "$schema": "node_modules/@openapitools/openapi-generator-cli/config.schema.json",
  "spaces": 2,
  "generator-cli": {
    "version": "7.2.0",
    "generators": {
      "typescript-axios": {
        "generatorName": "typescript-axios",
        "output": "src/api/generated",
        "glob": "openapi.json"
      }
    }
  }
}
EOF
    
    npm pkg set scripts.generate:api="openapi-generator-cli generate"
    
    log_success "API 文档生成配置完成"
}

setup_monitoring() {
    log_info "======================================"
    log_info "5. 设置监控体系"
    log_info "======================================"
    
    cd "$PROJECT_ROOT"
    
    log_info "检查监控配置..."
    if [ -d "config/monitoring" ]; then
        log_success "监控配置目录已存在"
    else
        log_warn "监控配置目录不存在"
    fi
    
    log_info "创建监控启动脚本..."
    cat > scripts/start-monitoring.sh << 'EOFSCRIPT'
#!/bin/bash

set -e

cd "$(dirname "$0")/../config/monitoring"

echo "启动监控服务..."
podman-compose -f podman-compose.yml up -d

echo "等待服务启动..."
sleep 10

echo "监控服务已启动:"
echo "  Prometheus: http://localhost:9090"
echo "  Grafana: http://localhost:3000 (admin/admin)"
echo "  AlertManager: http://localhost:9093"
EOFSCRIPT
    
    chmod +x scripts/start-monitoring.sh
    
    log_info "创建前端性能监控集成..."
    cd "$FRONTEND_ROOT"
    
    mkdir -p src/utils/monitoring
    cat > src/utils/monitoring/performance.ts << 'EOF'
export class PerformanceMonitor {
  private static instance: PerformanceMonitor

  private constructor() {
    this.init()
  }

  static getInstance(): PerformanceMonitor {
    if (!PerformanceMonitor.instance) {
      PerformanceMonitor.instance = new PerformanceMonitor()
    }
    return PerformanceMonitor.instance
  }

  private init() {
    if (typeof window === 'undefined') return

    this.observeNavigation()
    this.observeResources()
    this.observeLongTasks()
  }

  private observeNavigation() {
    window.addEventListener('load', () => {
      const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming
      if (navigation) {
        console.log('Navigation Timing:', {
          dns: navigation.domainLookupEnd - navigation.domainLookupStart,
          tcp: navigation.connectEnd - navigation.connectStart,
          request: navigation.responseStart - navigation.requestStart,
          response: navigation.responseEnd - navigation.responseStart,
          dom: navigation.domContentLoadedEventEnd - navigation.domContentLoadedEventStart,
          load: navigation.loadEventEnd - navigation.loadEventStart,
        })
      }
    })
  }

  private observeResources() {
    const observer = new PerformanceObserver((list) => {
      list.getEntries().forEach((entry) => {
        if (entry.duration > 1000) {
          console.warn('Slow Resource:', entry.name, entry.duration)
        }
      })
    })
    observer.observe({ entryTypes: ['resource'] })
  }

  private observeLongTasks() {
    if ('PerformanceObserver' in window) {
      try {
        const observer = new PerformanceObserver((list) => {
          list.getEntries().forEach((entry) => {
            console.warn('Long Task:', entry.duration)
          })
        })
        observer.observe({ entryTypes: ['longtask'] })
      } catch (e) {
        console.log('Long Task API not supported')
      }
    }
  }

  public markStart(name: string) {
    performance.mark(`${name}-start`)
  }

  public markEnd(name: string) {
    performance.mark(`${name}-end`)
    performance.measure(name, `${name}-start`, `${name}-end`)
    const measure = performance.getEntriesByName(name)[0]
    console.log(`${name}:`, measure.duration)
  }
}

export const performanceMonitor = PerformanceMonitor.getInstance()
EOF
    
    log_success "监控体系配置完成"
}

setup_cicd() {
    log_info "======================================"
    log_info "6. 增强 CI/CD 配置"
    log_info "======================================"
    
    cd "$PROJECT_ROOT"
    
    log_info "创建前端 CI 工作流..."
    mkdir -p .github/workflows
    cat > .github/workflows/frontend-ci.yml << 'EOF'
name: Frontend CI

on:
  push:
    branches: [ main, develop ]
    paths:
      - 'apex-admin-web/**'
  pull_request:
    branches: [ main, develop ]
    paths:
      - 'apex-admin-web/**'

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Setup Node.js
      uses: actions/setup-node@v4
      with:
        node-version: '18'
        cache: 'npm'
        cache-dependency-path: apex-admin-web/package-lock.json
    
    - name: Install dependencies
      working-directory: apex-admin-web
      run: npm ci
    
    - name: Run type check
      working-directory: apex-admin-web
      run: npm run build
    
    - name: Run unit tests
      working-directory: apex-admin-web
      run: npm run test:coverage
    
    - name: Upload coverage
      uses: codecov/codecov-action@v3
      with:
        directory: apex-admin-web/coverage
    
    - name: Run E2E tests
      working-directory: apex-admin-web
      run: npm run test:e2e
    
    - name: Upload E2E report
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: playwright-report
        path: apex-admin-web/playwright-report/

  build:
    runs-on: ubuntu-latest
    needs: test
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Setup Node.js
      uses: actions/setup-node@v4
      with:
        node-version: '18'
        cache: 'npm'
        cache-dependency-path: apex-admin-web/package-lock.json
    
    - name: Install dependencies
      working-directory: apex-admin-web
      run: npm ci
    
    - name: Build
      working-directory: apex-admin-web
      run: npm run build
    
    - name: Analyze bundle
      working-directory: apex-admin-web
      run: npm run analyze
    
    - name: Upload build artifacts
      uses: actions/upload-artifact@v3
      with:
        name: dist
        path: apex-admin-web/dist/
EOF
    
    log_success "CI/CD 配置完成"
}

setup_security_scan() {
    log_info "======================================"
    log_info "7. 设置安全扫描"
    log_info "======================================"
    
    cd "$FRONTEND_ROOT"
    
    log_info "安装安全审计工具..."
    npm install -D npm-audit-html snyk
    
    npm pkg set scripts.audit="npm audit --json | npm-audit-html --output ./reports/audit.html"
    npm pkg set scripts.audit:fix="npm audit fix"
    
    log_info "创建安全扫描脚本..."
    cat > scripts/security-scan.sh << 'EOFSCRIPT'
#!/bin/bash

set -e

echo "运行 npm 安全审计..."
npm audit --production

echo "检查过期依赖..."
npx npm-check-updates

echo "运行 Snyk 扫描..."
if command -v snyk &> /dev/null; then
    snyk test
else
    echo "Snyk 未安装，跳过"
fi

echo "安全扫描完成"
EOFSCRIPT
    
    chmod +x scripts/security-scan.sh
    
    cd "$PROJECT_ROOT"
    
    log_info "创建 OWASP Dependency Check 配置..."
    cat > scripts/owasp-check.sh << 'EOFSCRIPT'
#!/bin/bash

set -e

echo "运行 OWASP Dependency Check..."

if ! command -v dependency-check &> /dev/null; then
    echo "安装 OWASP Dependency Check..."
    brew install dependency-check || echo "请手动安装 dependency-check"
    exit 1
fi

dependency-check \
    --project "Apex Scaffold" \
    --scan . \
    --exclude "**/node_modules/**" \
    --exclude "**/target/**" \
    --out ./reports/dependency-check \
    --format HTML \
    --format JSON

echo "依赖检查报告: ./reports/dependency-check/dependency-check-report.html"
EOFSCRIPT
    
    chmod +x scripts/owasp-check.sh
    
    log_success "安全扫描配置完成"
}

setup_performance_test() {
    log_info "======================================"
    log_info "8. 设置性能测试"
    log_info "======================================"
    
    cd "$FRONTEND_ROOT"
    
    log_info "安装性能测试工具..."
    npm install -D lighthouse cli-progress
    
    log_info "创建 Lighthouse 测试脚本..."
    mkdir -p scripts/performance
    cat > scripts/performance/lighthouse-test.sh << 'EOFSCRIPT'
#!/bin/bash

set -e

URL="${1:-http://localhost:5173}"
OUTPUT_DIR="./reports/lighthouse"

mkdir -p "$OUTPUT_DIR"

echo "运行 Lighthouse 测试: $URL"

npx lighthouse "$URL" \
    --output html \
    --output json \
    --output-path "$OUTPUT_DIR/report" \
    --chrome-flags="--headless" \
    --preset=desktop

echo "Lighthouse 报告已生成: $OUTPUT_DIR/report.html"
EOFSCRIPT
    
    chmod +x scripts/performance/lighthouse-test.sh
    
    log_info "创建 JMeter 测试计划模板..."
    cd "$PROJECT_ROOT"
    mkdir -p scripts/performance/jmeter
    cat > scripts/performance/jmeter/api-load-test.jmx << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Apex API Load Test">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
        <collectionProp name="Arguments.arguments">
          <elementProp name="HOST" elementType="Argument">
            <stringProp name="Argument.name">HOST</stringProp>
            <stringProp name="Argument.value">localhost</stringProp>
          </elementProp>
          <elementProp name="PORT" elementType="Argument">
            <stringProp name="Argument.name">PORT</stringProp>
            <stringProp name="Argument.value">8080</stringProp>
          </elementProp>
          <elementProp name="THREADS" elementType="Argument">
            <stringProp name="Argument.name">THREADS</stringProp>
            <stringProp name="Argument.value">100</stringProp>
          </elementProp>
          <elementProp name="RAMP_TIME" elementType="Argument">
            <stringProp name="Argument.name">RAMP_TIME</stringProp>
            <stringProp name="Argument.value">60</stringProp>
          </elementProp>
          <elementProp name="DURATION" elementType="Argument">
            <stringProp name="Argument.name">DURATION</stringProp>
            <stringProp name="Argument.value">300</stringProp>
          </elementProp>
        </collectionProp>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="API Users">
        <stringProp name="ThreadGroup.num_threads">${THREADS}</stringProp>
        <stringProp name="ThreadGroup.ramp_time">${RAMP_TIME}</stringProp>
        <stringProp name="ThreadGroup.duration">${DURATION}</stringProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Health Check">
          <stringProp name="HTTPSampler.domain">${HOST}</stringProp>
          <stringProp name="HTTPSampler.port">${PORT}</stringProp>
          <stringProp name="HTTPSampler.path">/actuator/health</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
EOF
    
    log_info "创建性能测试执行脚本..."
    cat > scripts/performance/run-load-test.sh << 'EOFSCRIPT'
#!/bin/bash

set -e

JMETER_HOME="${JMETER_HOME:-/usr/local/Cellar/jmeter/5.6.3/libexec}"
TEST_PLAN="${1:-scripts/performance/jmeter/api-load-test.jmx}"
RESULTS_DIR="./reports/performance"

mkdir -p "$RESULTS_DIR"

if [ ! -f "$JMETER_HOME/bin/jmeter" ]; then
    echo "JMeter 未安装，请先安装: brew install jmeter"
    exit 1
fi

echo "运行 JMeter 负载测试..."
"$JMETER_HOME/bin/jmeter" \
    -n -t "$TEST_PLAN" \
    -l "$RESULTS_DIR/results.jtl" \
    -e -o "$RESULTS_DIR/html-report"

echo "性能测试报告: $RESULTS_DIR/html-report/index.html"
EOFSCRIPT
    
    chmod +x scripts/performance/run-load-test.sh
    
    npm pkg set scripts.perf:frontend="npm run build && scripts/performance/lighthouse-test.sh"
    
    log_success "性能测试配置完成"
}

create_summary_doc() {
    log_info "创建优化任务总结文档..."
    
    cd "$PROJECT_ROOT"
    cat > docs/optimization-tasks-summary.md << 'EOF'
# 优化任务总结

本文档记录了项目优化任务的配置和使用方法。

## 1. Vitest 单元测试

### 配置文件
- `vitest.config.ts`: Vitest 配置
- `src/test/setup.ts`: 测试环境设置
- `src/utils/__tests__/*.spec.ts`: 单元测试示例

### 使用命令
```bash
cd /Users/work/apex-admin-web
npm run test              # 运行所有测试
npm run test:ui           # 打开测试 UI
npm run test:coverage     # 生成覆盖率报告
```

### 测试覆盖率目标
- 语句覆盖率: ≥ 80%
- 分支覆盖率: ≥ 75%
- 函数覆盖率: ≥ 80%
- 行覆盖率: ≥ 80%

## 2. Playwright E2E 测试

### 配置文件
- `playwright.config.ts`: Playwright 配置
- `e2e/*.spec.ts`: E2E 测试用例

### 使用命令
```bash
npm run test:e2e          # 运行 E2E 测试
npm run test:e2e:ui       # 打开测试 UI
npm run test:e2e:report   # 查看测试报告
```

### 测试场景
- 用户登录流程
- 权限验证
- 关键业务流程
- 响应式布局测试

## 3. 构建产物分析

### 配置文件
- `vite.config.ts`: 集成 rollup-plugin-visualizer

### 使用命令
```bash
npm run analyze           # 构建并打开分析报告
```

### 优化建议
- Element Plus 单独打包
- Vue 全家桶合并为 vendor chunk
- ECharts 独立打包
- 代码分割优化

## 4. API 文档生成

### 后端 API 文档
```bash
cd /Users/work/apex-scaffold
./scripts/export-api-docs.sh
```

生成的文档位于 `docs/api/`：
- `openapi.json`: OpenAPI 规范（JSON）
- `openapi.yaml`: OpenAPI 规范（YAML）

在线文档: http://localhost:8080/swagger-ui.html

### 前端 API 客户端生成
```bash
cd /Users/work/apex-admin-web
npm run generate:api
```

生成的 TypeScript 客户端位于 `src/api/generated/`

## 5. 监控体系

### 启动监控服务
```bash
cd /Users/work/apex-scaffold
./scripts/start-monitoring.sh
```

### 监控端点
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- AlertManager: http://localhost:9093

### 前端性能监控
前端集成了 PerformanceMonitor，自动收集：
- 页面导航时序
- 资源加载时长
- 长任务检测

## 6. CI/CD 配置

### GitHub Actions 工作流
- `.github/workflows/frontend-ci.yml`: 前端 CI 流水线
  - 类型检查
  - 单元测试
  - E2E 测试
  - 构建产物分析

### 本地验证
```bash
cd /Users/work/apex-admin-web

npm run build             # 类型检查 + 构建
npm run test:coverage     # 单元测试
npm run test:e2e          # E2E 测试
npm run analyze           # 构建分析
```

## 7. 安全扫描

### 前端安全审计
```bash
cd /Users/work/apex-admin-web
npm run audit             # 生成审计报告
npm run audit:fix         # 自动修复漏洞
./scripts/security-scan.sh # 完整安全扫描
```

### 后端依赖检查
```bash
cd /Users/work/apex-scaffold
./scripts/owasp-check.sh  # OWASP Dependency Check
```

### 安全报告
- `reports/audit.html`: npm 审计报告
- `reports/dependency-check/`: OWASP 检查报告

## 8. 性能测试

### 前端性能测试
```bash
cd /Users/work/apex-admin-web
npm run perf:frontend     # Lighthouse 性能测试
./scripts/performance/lighthouse-test.sh http://localhost:5173
```

### 后端负载测试
```bash
cd /Users/work/apex-scaffold

# 安装 JMeter
brew install jmeter

# 运行负载测试
./scripts/performance/run-load-test.sh
```

### 性能指标
- Lighthouse 评分 ≥ 90
- FCP (First Contentful Paint) < 1.5s
- LCP (Largest Contentful Paint) < 2.5s
- TTI (Time to Interactive) < 3.5s
- CLS (Cumulative Layout Shift) < 0.1

## 日常开发检查清单

### 提交前检查
```bash
cd /Users/work/apex-admin-web

# 1. 代码质量
npm run build             # 类型检查

# 2. 测试
npm run test              # 单元测试

# 3. 安全
npm run audit             # 安全审计
```

### 定期检查（每周/每月）
```bash
# 1. E2E 测试
npm run test:e2e

# 2. 构建分析
npm run analyze

# 3. 性能测试
npm run perf:frontend

# 4. 依赖更新
npx npm-check-updates
```

## 后续优化建议

### 短期（1-2 周）
1. ✅ 完成 Vitest 单元测试配置
2. ✅ 完成 Playwright E2E 测试配置
3. ✅ 集成构建产物分析
4. ✅ 完善 API 文档生成

### 中期（1 个月）
5. ✅ 完善监控体系配置
6. ✅ 增强 CI/CD 流水线
7. ✅ 集成安全扫描工具
8. ✅ 配置性能测试

### 长期（持续）
9. 提高测试覆盖率到 80%+
10. 优化构建产物大小
11. 持续性能优化
12. 完善监控告警规则

## 参考资料

### 测试
- [Vitest 文档](https://vitest.dev/)
- [Playwright 文档](https://playwright.dev/)
- [Vue Test Utils](https://test-utils.vuejs.org/)

### 性能
- [Web Vitals](https://web.dev/vitals/)
- [Lighthouse 文档](https://developer.chrome.com/docs/lighthouse/)
- [Vite 性能优化](https://vitejs.dev/guide/performance.html)

### 安全
- [npm audit](https://docs.npmjs.com/cli/v9/commands/npm-audit)
- [Snyk](https://snyk.io/)
- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)

### CI/CD
- [GitHub Actions](https://docs.github.com/en/actions)
- [Codecov](https://about.codecov.io/)
EOF
    
    log_success "总结文档创建完成: docs/optimization-tasks-summary.md"
}

print_usage() {
    cat << EOF

使用方法:
    $0 [选项]

选项:
    --all               执行所有优化任务
    --vitest            设置 Vitest 单元测试
    --playwright        设置 Playwright E2E 测试
    --bundle-analysis   设置构建产物分析
    --api-docs          设置 API 文档生成
    --monitoring        设置监控体系
    --cicd              增强 CI/CD 配置
    --security          设置安全扫描
    --performance       设置性能测试
    --check-env         仅检查环境
    --help              显示帮助信息

示例:
    $0 --all                          # 执行所有优化任务
    $0 --vitest --playwright          # 仅设置测试框架
    $0 --check-env                    # 检查环境

EOF
}

main() {
    if [ $# -eq 0 ]; then
        print_usage
        exit 0
    fi
    
    local run_all=false
    local tasks=()
    
    while [ $# -gt 0 ]; do
        case "$1" in
            --all)
                run_all=true
                ;;
            --vitest)
                tasks+=("vitest")
                ;;
            --playwright)
                tasks+=("playwright")
                ;;
            --bundle-analysis)
                tasks+=("bundle-analysis")
                ;;
            --api-docs)
                tasks+=("api-docs")
                ;;
            --monitoring)
                tasks+=("monitoring")
                ;;
            --cicd)
                tasks+=("cicd")
                ;;
            --security)
                tasks+=("security")
                ;;
            --performance)
                tasks+=("performance")
                ;;
            --check-env)
                check_environment
                exit $?
                ;;
            --help)
                print_usage
                exit 0
                ;;
            *)
                log_error "未知选项: $1"
                print_usage
                exit 1
                ;;
        esac
        shift
    done
    
    log_info "======================================"
    log_info "Apex Scaffold 优化任务配置脚本"
    log_info "======================================"
    
    check_environment || exit 1
    
    if [ "$run_all" = true ]; then
        setup_vitest
        setup_playwright
        setup_bundle_analysis
        setup_api_docs
        setup_monitoring
        setup_cicd
        setup_security_scan
        setup_performance_test
        create_summary_doc
    else
        for task in "${tasks[@]}"; do
            case "$task" in
                vitest)
                    setup_vitest
                    ;;
                playwright)
                    setup_playwright
                    ;;
                bundle-analysis)
                    setup_bundle_analysis
                    ;;
                api-docs)
                    setup_api_docs
                    ;;
                monitoring)
                    setup_monitoring
                    ;;
                cicd)
                    setup_cicd
                    ;;
                security)
                    setup_security_scan
                    ;;
                performance)
                    setup_performance_test
                    ;;
            esac
        done
        
        if [ ${#tasks[@]} -gt 0 ]; then
            create_summary_doc
        fi
    fi
    
    log_info "======================================"
    log_success "优化任务配置完成！"
    log_info "======================================"
    log_info "查看详细文档: $PROJECT_ROOT/docs/optimization-tasks-summary.md"
    log_info ""
    log_info "快速开始:"
    log_info "  cd /Users/work/apex-admin-web"
    log_info "  npm run test              # 运行单元测试"
    log_info "  npm run test:e2e          # 运行 E2E 测试"
    log_info "  npm run analyze           # 分析构建产物"
    log_info ""
}

main "$@"

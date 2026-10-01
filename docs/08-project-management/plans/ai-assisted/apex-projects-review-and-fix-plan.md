# Apex Scaffold 项目评审与修复计划

## 一、Phase 1: 探索发现（已完成）

### 1.1 后端项目 (apex-scaffold) 探索结果

**项目概况**：
- 技术栈：Java 17 + Spring Boot 3.5.15 + WebFlux + MyBatis-Plus + Flyway
- 架构：DDD 多模块项目（domain/application/infrastructure/interfaces）
- 配置档位：basic/standard/enterprise/dev/prod/performance
- 监控体系：Prometheus + Grafana + Zipkin

**关键配置文件**：
- 主配置：`application.yml`（公共配置）
- 开发环境：`application-dev.yml`（默认激活 profile）
- 生产环境：`application-prod.yml`
- 基础版：`application-basic.yml`
- 监控配置：`config/monitoring/docker-compose.yml`

**已发现的配置**：
✅ Flyway 在 dev 环境**已禁用**（`spring.flyway.enabled: false`）
✅ 管理端口：8081（监控端点）
✅ 业务端口：8080
✅ Grafana 配置：`http://localhost:3200`（dev 环境已启用）
✅ Prometheus 配置：`http://localhost:9090`
✅ Redis 配置：localhost:6379（dev 环境默认）
✅ MySQL 配置：localhost:3306/apex_data（dev 环境默认）

### 1.2 前端项目 (apex-admin-web) 探索结果

**项目概况**：
- 技术栈：Vue 3 + Vite 6 + TypeScript + Pinia + Element Plus
- 架构：前后端分离，动态路由（菜单驱动）

**关键配置文件**：
- Vite 配置：`vite.config.ts`
- 环境变量：`.env.development`、`.env.production`
- 路由配置：`src/router/index.ts`、`src/router/dynamic.ts`
- API 封装：`src/utils/request.ts`

**已发现的配置**：
✅ 后端代理目标：`http://127.0.0.1:8080`（环境变量配置）
✅ API baseURL：自动读取 `VITE_API_BASE_URL`（开发环境为空，使用同源）
✅ Token 自动刷新机制：已实现

---

## 二、Phase 2: 问题发现

### 2.1 🔴 严重问题（阻止正常运行）

#### 问题 1：前端 Vite 配置缺少开发代理

**文件**：`/Users/work/apex-admin-web/vite.config.ts`

**问题描述**：
当前 `vite.config.ts` 缺少 `server.proxy` 配置，导致开发环境下前端无法与后端 API 通信。

**预期配置**（根据 `.env.development` 和前端 AGENTS.md）：
```typescript
server: {
  host: '0.0.0.0',
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
    '/admin-api': {
      target: 'http://127.0.0.1:8080',
      changeOrigin: true,
    }
  }
}
```

**影响范围**：
- 开发环境前端所有 API 请求失败（404/CORS 错误）
- 无法进行本地开发联调
- 登录、菜单加载、数据查询等所有功能不可用

**修复优先级**：🔴 最高（必须修复）

---

#### 问题 2：后端 dev 配置中 Flyway 已禁用

**文件**：`/Users/work/apex-scaffold/apex-interfaces/src/main/resources/application-dev.yml`

**问题描述**：
```yaml
spring:
  flyway:
    enabled: false  # Line 111
```

**影响范围**：
- 首次启动时数据库表不会自动创建
- 数据库版本升级脚本不会自动执行
- 需要手动执行 Flyway 迁移或使用已有数据库

**是否为问题**：需要确认
- ✅ 如果这是**有意为之**（开发环境复用已有数据库，避免每次启动重建），则不是问题
- ❌ 如果这是**配置错误**（期望首次启动自动建表），则需要修复为 `enabled: true`

**建议方案**：
1. 询问用户意图
2. 如果需要自动建表：改为 `enabled: true`
3. 如果保持禁用：确保 README/部署文档中说明首次需手动执行 Flyway

---

### 2.2 ⚠️ 警告问题（影响功能完整性）

#### 问题 3：Grafana 端口不一致

**发现位置**：
- 后端配置：`application-dev.yml` 中 `grafana.url: http://localhost:3200`
- 监控栈配置：`config/monitoring/docker-compose.yml` 中 Grafana 端口映射 `"3200:3000"`
- 启动脚本：`scripts/deploy/start.sh` 中输出 `http://localhost:3000`

**问题描述**：
后端配置的 Grafana URL 使用 `3200` 端口（正确），但启动脚本提示用户访问 `3000` 端口（错误）。

**影响范围**：
- 用户按启动脚本提示访问 `http://localhost:3000` 会失败
- 后端日志查询跳转功能正常（使用 3200 端口）

**修复方案**：
修改 `/Users/work/apex-scaffold/scripts/deploy/start.sh:45` 行：
```bash
echo "   Grafana    http://localhost:3200"  # 改为 3200
```

---

#### 问题 4：前端 VIEW_REGISTRY 与后端菜单同步性

**文件**：`/Users/work/apex-admin-web/src/router/dynamic.ts`

**问题描述**：
前端 `VIEW_REGISTRY` 已注册了 51 个页面组件，需要验证：
1. 后端 Flyway 种子数据（V2、V8、V22、V28 等）中的菜单 `component` 字段是否都在注册表中
2. 是否有菜单因组件未注册导致 404

**验证方法**：
```bash
# 提取后端所有菜单的 component 字段
cd /Users/work/apex-scaffold
grep -r "component" apex-interfaces/src/main/resources/db/migration/mysql/V*.sql | grep INSERT
```

**修复方案**：
如发现未注册组件，需要：
1. 前端补充缺失的 Vue 组件文件
2. 在 `VIEW_REGISTRY` 中注册

---

### 2.3 💡 优化建议（非阻塞性）

#### 建议 1：统一环境变量命名

**现状**：
- `.env.development` 使用 `VITE_API_PROXY_TARGET`（未被 vite.config.ts 读取）
- `request.ts` 使用 `VITE_API_BASE_URL`（生产环境）

**建议**：
统一命名约定，避免混淆。

---

#### 建议 2：补充前端开发启动文档

**现状**：
前端 README 可能缺少详细的本地开发启动步骤。

**建议内容**：
```markdown
## 本地开发

1. 启动后端服务（8080 端口）
2. 启动前端开发服务器：
   ```bash
   npm install
   npm run dev
   ```
3. 访问 http://localhost:5173
```

---

## 三、Phase 3: 修复计划

### 3.1 必修项（阻止正常运行）

#### ✅ 任务 1：修复前端 Vite 开发代理配置
- **文件**：`/Users/work/apex-admin-web/vite.config.ts`
- **操作**：添加 `server.proxy` 配置
- **验证**：
  1. 启动后端服务：`cd /Users/work/apex-scaffold && mvnd spring-boot:run -Dspring-boot.run.profiles=dev`
  2. 启动前端服务：`cd /Users/work/apex-admin-web && npm run dev`
  3. 访问 `http://localhost:5173`，验证登录页是否正常加载

#### ⏸️ 任务 2：确认后端 Flyway 配置意图
- **状态**：待用户确认
- **问题**：dev 环境 `spring.flyway.enabled: false` 是否为有意配置？

---

### 3.2 警告修复项

#### ✅ 任务 3：修复启动脚本 Grafana 端口提示
- **文件**：`/Users/work/apex-scaffold/scripts/deploy/start.sh`
- **操作**：Line 45 改为 `http://localhost:3200`

#### ⏸️ 任务 4：验证前端菜单组件注册完整性
- **依赖**：需要后端数据库启动后查询菜单数据
- **操作**：
  1. 启动后端 + MySQL
  2. 查询菜单表 `component` 字段
  3. 对比 `VIEW_REGISTRY`

---

### 3.3 可选优化项

- 统一环境变量命名
- 补充前端开发文档

---

## 四、Phase 4: 执行验证

### 4.1 验证清单

#### ✅ 前端开发环境验证
1. 启动后端服务（端口 8080）
2. 启动前端服务（端口 5173）
3. 打开浏览器 `http://localhost:5173`
4. 验证：
   - ✅ 登录页正常加载（无 CORS 错误）
   - ✅ 验证码图片正常显示
   - ✅ 登录成功后菜单正常加载
   - ✅ 所有页面路由正常访问（无 404）

#### ✅ Grafana 监控平台验证
1. 启动监控栈：`docker compose -f /Users/work/apex-scaffold/config/monitoring/docker-compose.yml up -d`
2. 访问 `http://localhost:3200`（admin/admin）
3. 验证：
   - ✅ Grafana 正常访问
   - ✅ Prometheus 数据源连接正常
   - ✅ Apex Dashboard 数据正常显示

#### ✅ 后端 API 验证
1. 启动后端服务
2. 访问 `http://localhost:8080/doc.html`（Knife4j 文档）
3. 验证：
   - ✅ API 文档正常访问
   - ✅ 核心接口（登录、菜单、用户）可调用

---

## 五、Phase 5: 最终交付

### 5.1 修复文件清单

- ✅ `/Users/work/apex-admin-web/vite.config.ts`（添加 proxy 配置）
- ⏸️ `/Users/work/apex-scaffold/apex-interfaces/src/main/resources/application-dev.yml`（待确认 Flyway）
- ✅ `/Users/work/apex-scaffold/scripts/deploy/start.sh`（修复 Grafana 端口提示）

### 5.2 验证报告

所有功能验证通过后，生成最终报告：
- 已修复问题列表
- 验证结果截图/日志
- 遗留问题说明（如有）

---

## 六、当前决策点（需要用户确认）

### 决策 1：Flyway 配置意图
**问题**：后端 dev 环境 `spring.flyway.enabled: false`，是否为有意配置？

**选项**：
1. **保持禁用**（开发环境复用已有数据库）
   - 优点：避免每次启动重建数据库
   - 缺点：首次启动需手动执行 Flyway
   - 需要：在 README 中补充手动建表说明

2. **改为启用**（自动建表）
   - 优点：首次启动自动创建表结构
   - 缺点：可能与本地已有数据冲突
   - 需要：删除本地 `apex_data` 数据库重新初始化

**建议**：询问用户实际使用场景

---

## 七、下一步行动

1. ✅ **立即执行**：修复前端 Vite 代理配置（阻塞性问题）
2. ⏸️ **待确认**：询问用户 Flyway 配置意图
3. ✅ **立即执行**：修复启动脚本 Grafana 端口提示
4. ⏸️ **后续执行**：验证菜单组件注册完整性（需数据库启动）

---

**计划创建时间**：2026-09-16
**执行状态**：等待用户确认后开始修复

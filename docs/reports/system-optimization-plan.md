# Apex Scaffold 系统全面优化与完善计划

## 项目概览

**项目名称**: Apex Scaffold  
**版本**: 1.2.0  
**技术栈**: Spring Boot 3.5.15 + WebFlux + MyBatis-Plus + MySQL + Redis + Sentinel  
**前端**: apex-admin-web (Vue 3 + Vite + TypeScript + Element Plus)  
**监控**: Prometheus + Grafana + Zipkin  

---

## 第一阶段：探索与现状分析（Phase 1）

### 1.1 项目结构分析

#### 后端模块结构
```
apex-scaffold/
├── apex-common/              # 公共模块（注解、工具类、上下文）
├── apex-domain/              # 领域模型（实体、仓储接口、领域服务）
├── apex-application/         # 应用服务层（DTO、Service、Assembler）
├── apex-infrastructure/      # 基础设施（持久化实现、外部服务适配）
├── apex-interfaces/          # 接口层（Handler、Router、Filter、配置）
├── apex-example/             # 示例模块
├── apex-generator/           # 代码生成器
├── apex-license-*/           # License管理模块
└── config/monitoring/        # 监控配置（Grafana、Prometheus）
```

#### 前端项目位置
- **主前端**: 无独立前端项目位于当前仓库
- **静态前端**: `apex-interfaces/src/main/resources/static/admin/` （原生HTML/JS实现）
- **文档提及**: `/Users/work/apex-admin-web` （独立Vue3前端工程，需确认是否存在）

#### 数据库脚本位置
- **Flyway迁移**: `apex-interfaces/src/main/resources/db/migration/` （V1-V20）
- **示例数据**: `apex-example/src/main/resources/db/init.sql`
- **测试迁移**: `apex-example/src/test/resources/db/example-migration/`

### 1.2 配置文件分析

#### 环境配置
- `application.yml` - 公共配置
- `application-dev.yml` - 开发环境
- `application-prod.yml` - 生产环境
- `application-basic.yml` - 基础版
- `application-standard.yml` - 标准版
- `application-enterprise.yml` - 企业版
- `application-performance.yml` - 性能优化版

#### 监控配置
- `config/monitoring/grafana/apex-dashboard.json` - Grafana监控面板
- `config/monitoring/prometheus/prometheus.yml` - Prometheus抓取配置
- `config/monitoring/prometheus/alert-rules.yml` - 告警规则
- `config/monitoring/docker-compose.yml` - 监控栈一键部署

### 1.3 API路由与接口分析

#### 已实现的Handler
1. **认证授权**: AuthHandler, AuthorizationHandler
2. **用户管理**: UserHandler, AdminUserManagementHandler
3. **系统管理**: MenuHandler, ConfigHandler, DictHandler, DeptHandler
4. **定时任务**: JobHandler
5. **监控日志**: 
   - LoginLogHandler - 登录日志
   - AuditLogHandler - 操作审计
   - ApiCallLogHandler - API调用日志
   - OnlineUserHandler - 在线用户
   - MonitorDashboardHandler - 监控大盘
6. **工作流**: WorkflowHandler
7. **文件管理**: FileHandler
8. **其他**: CaptchaHandler, HealthHandler, NotificationSseHandler

#### 路由注册情况
所有Handler已在`ApiRouter.java`中通过RouterFunction注册，路由前缀：
- `/api/v1/*` - 业务接口
- `/admin-api/v1/*` - 管理后台接口

### 1.4 监控体系现状

#### Grafana监控面板
**位置**: `config/monitoring/grafana/apex-dashboard.json`

**已有监控指标分组**:
1. 监控概览（服务可用性、请求QPS、响应时间、错误率）
2. 请求详情（P50/P95/P99延迟、状态码分布）
3. JVM内存（堆/非堆/Eden/Old Gen）
4. JVM线程（活跃/守护/峰值线程数）
5. JVM GC（Minor/Major GC次数与耗时）
6. 数据库连接池（HikariCP/Druid活跃连接数、连接池水位）
7. Redis监控（节点健康、命令QPS）
8. Sentinel治理（限流QPS、熔断统计）
9. 业务监控（登录/注册/上传/信用变动）
10. 安全指标（限流拒绝、IP封禁、XSS拦截）

**指标来源**: 
- 应用指标通过 `/actuator/prometheus` (端口8081) 暴露
- Sentinel指标通过独立端口8720暴露
- 指标采集由Prometheus完成

#### Prometheus配置
**抓取目标**:
- `apex-scaffold`: localhost:8081 (应用监控端点)
- `apex-sentinel`: localhost:8720 (Sentinel指标)
- `apex-scaffold-health`: 健康探针 (Blackbox Exporter)
- `grafana`: localhost:3000 (Grafana自监控)
- `zipkin`: localhost:9411 (链路追踪)

**告警规则**: `alert-rules.yml` 已定义10条规则

---

## 第二阶段：需求分析与任务分解（Phase 2）

### 2.1 任务1：前后端联调与404问题修复

#### 问题分析
1. **前端项目位置不明确**
   - 文档提及 `/Users/work/apex-admin-web` 但需确认是否存在
   - 后端有内嵌静态前端 `static/admin/`
   
2. **潜在404原因**
   - 前端路由与后端API路径不匹配
   - CORS跨域配置问题
   - 动态路由未正确注册
   - 权限配置导致接口403被前端误判为404

#### 解决方案
1. **确认前端项目位置**
   - 检查 `/Users/work/apex-admin-web` 是否存在
   - 如不存在，使用内嵌静态前端或创建新前端项目

2. **API路由对接检查**
   - 验证所有前端API调用的路径是否与后端RouterFunction匹配
   - 检查权限配置（sys_menu表、sys_permission表）
   - 确保CORS配置正确（dev环境已配置localhost:5173）

3. **缺失功能补齐**
   - 根据文档，API调用日志页面可能缺失
   - 补齐前端视图：`views/monitor/api-call-log/index.vue`
   - 注册动态路由

### 2.2 任务2：Grafana监控面板增强

#### 当前监控缺口分析
1. **JVM监控增强需求**
   - ✅ 已有: 堆内存、线程、GC基础指标
   - ❌ 缺失: 
     - JVM进程内存（RSS/VSS）- 已有ProcessMemoryMetrics类但需验证
     - 类加载统计
     - 直接内存使用
     - Metaspace详细监控
     - CodeCache使用率

2. **Sentinel监控增强需求**
   - ✅ 已有: 限流QPS、熔断统计
   - ❌ 缺失:
     - 热点参数限流详情
     - 系统自适应保护规则触发统计
     - 授权规则拒绝统计
     - 各资源降级时长分布

3. **网关监控增强需求**
   - ✅ 已有: 请求QPS、响应时间、错误率
   - ❌ 缺失:
     - 慢请求Top排行
     - 接口调用链路追踪关联
     - 请求大小分布
     - 响应大小分布

4. **熔断限流监控增强需求**
   - ✅ 已有: Sentinel基础指标
   - ❌ 缺失:
     - Resilience4j熔断器状态（已有/actuator/circuitbreakers端点）
     - 各熔断器实例的打开/半开/关闭状态
     - 熔断器失败率、慢调用率时序图
     - 限流器令牌桶水位

#### 增强方案

**新增监控面板**:
1. **JVM进程与内存详情**
   - 进程内存（RSS/VSS）
   - Metaspace使用率与趋势
   - CodeCache使用率
   - 类加载数量与卸载趋势
   - 直接内存使用（如果可获取）

2. **Sentinel深度监控**
   - 各资源限流QPS与拒绝率
   - 热点参数限流触发次数
   - 系统规则触发统计（CPU、线程数、RT）
   - 授权规则拒绝统计
   - 降级规则触发时序图

3. **网关与接口性能**
   - 慢请求Top10接口
   - 请求/响应体大小分布
   - 各路径P99延迟趋势
   - 5xx/4xx错误Top接口

4. **熔断器与限流器状态**
   - Resilience4j熔断器状态面板
   - 各熔断器失败率与慢调用率
   - 熔断器状态转换事件
   - 限流器令牌获取成功率

### 2.3 任务3：配置文件与数据库脚本检查

#### 检查项
1. **配置文件完整性**
   - ✅ 开发环境配置齐全
   - ✅ 生产环境配置完整
   - ⚠️ 需检查环境变量占位符是否都有默认值或注释说明

2. **数据库脚本完整性**
   - ✅ Flyway迁移脚本V1-V20完整
   - ⚠️ 需验证脚本与当前代码实体类是否匹配
   - ⚠️ 需检查初始化数据是否完整（sys_menu、sys_permission、sys_role）

3. **配置一致性检查**
   - application.yml与代码Properties类映射
   - Dockerfile中的环境变量与配置文件占位符
   - docker-compose配置与应用配置的一致性

#### 检查方法
1. **自动化检查脚本**
   - 扫描所有@ConfigurationProperties类
   - 提取配置key
   - 与yml文件对比，生成缺失配置报告

2. **数据库表结构对比**
   - 从Flyway脚本生成DDL
   - 从Entity类生成DDL
   - 对比差异

### 2.4 任务4：文档生成

#### 文档清单
1. **数据库设计文档**
   - 表结构说明（字段名、类型、约束、注释）
   - 索引设计
   - 外键关系

2. **E-R图**
   - 核心业务实体关系图
   - 权限体系E-R图
   - 工作流E-R图

3. **系统分析文档**
   - 业务场景分析
   - 用例图
   - 核心流程时序图

4. **系统设计文档**
   - 系统架构设计
   - 模块划分与职责
   - 技术选型说明
   - 设计模式应用

5. **系统架构图**
   - 逻辑架构图（DDD四层架构）
   - 物理架构图（服务部署）
   - 技术架构图（技术栈）

6. **模块服务结构图**
   - 模块依赖关系
   - 服务调用链路

7. **网络部署架构图**
   - 容器化部署架构
   - 网络拓扑
   - 负载均衡
   - 监控体系

8. **业务流程图**
   - 用户注册登录流程
   - 权限验证流程
   - 工作流审批流程
   - 文件上传下载流程

### 2.5 任务5：相关文档、脚本、配置文件同步调整

#### 调整内容
1. **README更新**
   - 最新功能列表
   - 部署说明
   - 监控接入说明
   - API文档链接

2. **部署脚本优化**
   - docker-compose完善
   - 启动脚本增加健康检查
   - 停止脚本优雅关闭

3. **测试代码完善**
   - 增加集成测试
   - 补充单元测试覆盖
   - 性能测试脚本

4. **配置文件注释**
   - 所有配置项添加中文注释
   - 敏感配置加🔒标识
   - 环境差异配置说明

---

## 第三阶段：详细实施方案（Phase 3）

### 3.1 前后端联调修复方案

#### 步骤1：前端项目确认
```bash
# 检查前端项目是否存在
ls -la /Users/work/apex-admin-web

# 如果存在，检查package.json
cat /Users/work/apex-admin-web/package.json

# 如果不存在，使用内嵌静态前端或提示用户创建
```

#### 步骤2：API对接检查清单
- [ ] 验证所有前端API调用路径
- [ ] 检查CORS配置
- [ ] 验证权限配置
- [ ] 测试所有功能页面

#### 步骤3：补齐API调用日志前端页面
如果apex-admin-web存在，按照以下步骤补齐：

**文件1**: `src/types/index.ts` - 添加类型定义
```typescript
export interface ApiCallLog {
  id: number
  serviceName?: string
  method?: string
  path?: string
  url?: string
  responseStatus?: string
  executionTime?: number
  status: number  // 0=成功 1=客户端错误 2=服务端错误 3=网络异常
  errorMsg?: string
  traceId?: string
  requestId?: string
  callTime?: string
  createTime?: string
}
```

**文件2**: `src/api/monitor.ts` - 添加API函数
```typescript
export interface ApiCallLogQuery extends PageQuery {
  serviceName?: string
  status?: number
  traceId?: string
  path?: string
}

export function listApiCallLogs(
  params: ApiCallLogQuery
): Promise<PageResponse<ApiCallLog>> {
  return get<PageResponse<ApiCallLog>>(
    '/admin-api/v1/api-call-logs',
    params as unknown as Record<string, unknown>
  )
}
```

**文件3**: `src/views/monitor/api-call-log/index.vue` - 创建视图组件
（参考audit/index.vue实现）

**文件4**: `src/router/dynamic.ts` - 注册路由
```typescript
'monitor/api-call-log/index': () => import('@/views/monitor/api-call-log/index.vue'),
```

### 3.2 Grafana监控增强方案

#### 方案1：JVM深度监控面板
在`apex-dashboard.json`中新增row：

**指标来源**:
- `jvm_classes_loaded` - 已加载类数量
- `jvm_classes_unloaded_total` - 卸载类总数
- `process_resident_memory_bytes` - 常驻内存（需ProcessMemoryMetrics）
- `process_virtual_memory_bytes` - 虚拟内存
- `jvm_memory_pool_bytes_used{pool="Metaspace"}` - Metaspace使用
- `jvm_memory_pool_bytes_used{pool="Code Cache"}` - CodeCache使用

**面板配置**:
```json
{
  "title": "JVM - 类加载与内存详情",
  "panels": [
    {
      "title": "类加载统计",
      "targets": [{
        "expr": "jvm_classes_loaded{application=\"apex-scaffold\"}"
      }]
    },
    {
      "title": "进程内存（RSS/VSS）",
      "targets": [{
        "expr": "process_resident_memory_bytes{application=\"apex-scaffold\"}"
      }, {
        "expr": "process_virtual_memory_bytes{application=\"apex-scaffold\"}"
      }]
    },
    {
      "title": "Metaspace使用率",
      "targets": [{
        "expr": "jvm_memory_pool_bytes_used{pool=\"Metaspace\"} / jvm_memory_pool_bytes_max{pool=\"Metaspace\"} * 100"
      }]
    }
  ]
}
```

#### 方案2：Sentinel深度监控
**新增指标**:
- `sentinel_pass_qps` - 通过QPS
- `sentinel_block_qps` - 阻塞QPS
- `sentinel_exception_qps` - 异常QPS
- `sentinel_rt` - 平均响应时间

**面板配置**: 按资源名称分组展示

#### 方案3：熔断器状态监控
**指标来源**:
- 调用 `/actuator/circuitbreakers` 端点
- 通过ServiceMonitor采集或编写自定义exporter

**面板内容**:
- 熔断器状态（CLOSED/OPEN/HALF_OPEN）
- 失败率、慢调用率时序图
- 熔断器打开/关闭事件时间线

### 3.3 配置检查与修复方案

#### 自动化检查脚本
```bash
#!/bin/bash
# check-config.sh - 配置完整性检查

echo "=== 检查配置文件 ==="

# 1. 检查必需环境变量
required_vars=(
  "JWT_SECRET"
  "DB_MASTER_URL"
  "REDIS_HOST"
)

for var in "${required_vars[@]}"; do
  if ! grep -q "$var" apex-interfaces/src/main/resources/application*.yml; then
    echo "⚠️  配置缺失: $var"
  fi
done

# 2. 检查数据库表与Entity一致性
echo "=== 检查数据库表结构 ==="
# TODO: 实现DDL对比逻辑

echo "✅ 配置检查完成"
```

### 3.4 文档生成方案

#### 工具选择
1. **数据库文档**: screw-core (螺丝刀)
2. **E-R图**: MySQL Workbench / dbdiagram.io
3. **架构图**: PlantUML / draw.io
4. **API文档**: Swagger/Knife4j (已集成)

#### 生成脚本示例
```xml
<!-- pom.xml 添加文档生成插件 -->
<plugin>
  <groupId>cn.smallbun.screw</groupId>
  <artifactId>screw-maven-plugin</artifactId>
  <version>1.0.5</version>
  <dependencies>
    <dependency>
      <groupId>com.zaxxer</groupId>
      <artifactId>HikariCP</artifactId>
      <version>4.0.3</version>
    </dependency>
  </dependencies>
  <configuration>
    <driverClassName>com.mysql.cj.jdbc.Driver</driverClassName>
    <jdbcUrl>jdbc:mysql://127.0.0.1:3306/apex_data</jdbcUrl>
    <username>root</username>
    <password>apex-root</password>
    <fileOutputDir>./docs/database</fileOutputDir>
    <openOutputDir>false</openOutputDir>
    <produceType>html</produceType>
  </configuration>
</plugin>
```

---

## 第四阶段：验证与测试计划（Phase 4）

### 4.1 前后端联调测试清单
- [ ] 登录功能
- [ ] 用户管理CRUD
- [ ] 菜单管理
- [ ] 角色权限管理
- [ ] 字典管理
- [ ] 参数配置
- [ ] 定时任务
- [ ] 登录日志查询
- [ ] 操作审计查询
- [ ] API调用日志查询 ⭐
- [ ] 在线用户管理
- [ ] 监控大盘数据展示
- [ ] 文件上传下载
- [ ] 工作流功能（如启用）

### 4.2 监控面板验证
- [ ] 所有面板正常加载
- [ ] 指标数据正常展示
- [ ] 图表时间范围正确
- [ ] 告警规则触发测试

### 4.3 配置验证
- [ ] dev环境启动成功
- [ ] prod环境启动成功
- [ ] 所有Profile切换正常
- [ ] 环境变量覆盖生效

### 4.4 文档验证
- [ ] 数据库文档准确
- [ ] E-R图清晰
- [ ] 架构图完整
- [ ] 流程图正确

---

## 第五阶段：交付物清单（Phase 5）

### 5.1 代码交付物
- [ ] 前端404问题修复（如有）
- [ ] 缺失前端页面补齐
- [ ] 后端配置文件优化

### 5.2 监控交付物
- [ ] 增强的Grafana监控面板JSON
- [ ] 新增Prometheus告警规则
- [ ] 监控指标说明文档

### 5.3 配置交付物
- [ ] 完善的配置文件（带详细注释）
- [ ] 环境变量说明文档
- [ ] 部署检查清单

### 5.4 数据库交付物
- [ ] 完整的Flyway迁移脚本
- [ ] 数据库设计文档（HTML/Markdown）
- [ ] E-R图（PNG/SVG）
- [ ] 初始化数据SQL

### 5.5 文档交付物
- [ ] 系统分析文档
- [ ] 系统设计文档
- [ ] 系统架构图
- [ ] 模块服务结构图
- [ ] 网络部署架构图
- [ ] 业务流程图
- [ ] 部署运维手册
- [ ] 开发规范文档

### 5.6 脚本交付物
- [ ] 启动脚本
- [ ] 停止脚本
- [ ] 健康检查脚本
- [ ] 数据库备份脚本
- [ ] 配置检查脚本

---

## 关键假设与约束

### 假设
1. `/Users/work/apex-admin-web` 前端项目存在并可访问
2. 数据库脚本基本正确，仅需验证细节
3. 监控栈（Prometheus/Grafana）已部署或可快速部署
4. 有权限修改配置文件和代码

### 约束
1. 不改变现有架构和技术栈
2. 保持向后兼容
3. 不引入新的外部依赖（除非必要）
4. 遵循项目现有代码规范

### 风险
1. 前端项目可能不存在，需要创建或使用静态前端
2. 部分监控指标可能需要应用代码修改才能暴露
3. 文档生成可能需要手工整理
4. 跨系统联调可能遇到网络或权限问题

---

## 下一步行动

### 立即确认事项
1. ✅ 确认 `/Users/work/apex-admin-web` 是否存在
2. ✅ 确认可以访问数据库
3. ✅ 确认可以访问Redis
4. ✅ 确认监控栈是否已部署

### 优先级排序
**P0 - 最高优先级**:
1. 前后端联调与404修复
2. 配置文件与数据库脚本验证

**P1 - 高优先级**:
3. Grafana监控面板增强（JVM、Sentinel、熔断器）
4. 核心文档生成（数据库设计、系统架构）

**P2 - 中优先级**:
5. 详细文档生成（E-R图、流程图）
6. 测试脚本完善

---

## 总结

本计划分为5个阶段，系统性地完成以下工作：
1. ✅ **探索分析**: 项目结构、配置、API、监控现状全面梳理
2. **需求分解**: 5大任务详细分析与方案设计
3. **实施方案**: 可操作的步骤和代码示例
4. **验证测试**: 完整的测试清单
5. **交付清单**: 明确的交付物

**预计工作量**: 3-5个工作日（根据前端项目实际情况调整）

**关键里程碑**:
- Day 1: 前后端联调修复 + 配置检查
- Day 2-3: 监控面板增强
- Day 4: 文档生成
- Day 5: 测试验证与交付

---

**文档版本**: v1.0  
**创建时间**: 2026-09-03  
**最后更新**: 2026-09-03

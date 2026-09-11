# Apex Scaffold 架构图表新人培训 - PPT大纲

> 基于 ONBOARDING_ARCHITECTURE_GUIDE.md 生成 | 2026-09-10

本文档提供完整的PPT培训课件大纲,包括18页幻灯片的详细内容设计。

---

## 📊 PPT 结构总览

**总页数**: 18页
**培训时长**: 2小时
**目标受众**: 新加入Apex Scaffold项目的团队成员
**视觉风格**: 现代企业风格,紫色渐变主题(#667eea → #764ba2)

---

## 🎬 幻灯片详细大纲

### 第一部分:开场与目标设定 (3页)

#### Slide 1: 封面
**布局**: 全屏渐变背景 + 居中标题
**内容**:
- 主标题: "Apex Scaffold 架构图表新人培训"
- 副标题: "快速理解项目架构 · 掌握核心技术栈 · 提升开发效率"
- 元信息: 日期(2026-09-10)、时长(2小时)、主讲(架构团队)
**视觉**: 紫色渐变背景(135deg, #667eea → #764ba2)

#### Slide 2: 培训目标
**布局**: 5个卡片式要点列表
**内容**:
1. ✅ 理解DDD四层架构设计
2. ✅ 掌握前后端技术栈选型及版本
3. ✅ 了解生产环境部署拓扑和运维流程
4. ✅ 熟悉核心业务流程实现
5. ✅ 学会快速查找架构文档
**视觉**: 每个要点带图标,浅蓝背景卡片,左侧紫色边框

#### Slide 3: 学习路径总览
**布局**: 4个阶段时间轴
**内容**:
- 第1阶段(30分钟): 架构基础 - DDD四层、技术栈、部署拓扑
- 第2阶段(30分钟): 核心业务流程 - JWT、RBAC、工作流、文件上传
- 第3阶段(30分钟): 基础设施与安全 - 数据库、缓存、限流、权限、安全
- 实践任务(30分钟): 环境搭建、创建接口、查看监控
**视觉**: 圆形编号徽章(渐变色),横向时间轴布局

---

### 第二部分:第1阶段 - 架构基础 (4页)

#### Slide 4: DDD四层逻辑架构
**布局**: 左侧架构图 + 右侧要点
**内容**:
- 左侧: 嵌入01_logical_architecture.svg图表
- 右侧要点:
  - 接口层: 32个Handler(路由+请求响应)
  - 应用层: 34个Service(用例编排+事务)
  - 领域层: 47个Repository接口(领域模型)
  - 基础设施层: PO/Mapper/RepositoryImpl
  - 依赖方向: 只能从上往下/从外向内
**实践建议**: 打开apex-interfaces目录,对照理解
**视觉**: 图表占60%,文字占40%

#### Slide 5: 全栈技术选型全景
**布局**: 上方图表 + 下方分类标签
**内容**:
- 上方: 嵌入03_technical_architecture.svg图表
- 下方关键版本标签:
  - 前端: Vue 3.5.13, Vite 6, TypeScript 5
  - 后端: Spring Boot 3.5.15, WebFlux, MyBatis-Plus
  - 中间件: MySQL 8.0, Redis 7.4, RocketMQ 5.3
  - 监控: Prometheus, Grafana(66个面板), Loki, Zipkin
**实践建议**: 对照pom.xml和package.json确认版本
**视觉**: 技术标签彩色编码(前端绿/后端蓝/中间件橙/监控紫)

#### Slide 6: 网络物理部署架构
**布局**: 中心图表 + 底部配置信息
**内容**:
- 中心: 嵌入02_physical_architecture.svg图表
- 5层拓扑说明:
  - 客户端层: 浏览器/移动端
  - DMZ安全层: Nginx反向代理
  - 应用服务层: Spring Boot实例
  - 数据存储层: MySQL/Redis/MinIO
  - 监控可观测层: Prometheus/Grafana/Loki
- 端口配置: 8080(业务)、8081(管理)
- Profile: basic(开发)/standard(标准)/enterprise(企业)
**实践建议**: `docker compose up -d` 启动本地环境
**视觉**: 层次结构清晰,配色区分不同层级

#### Slide 7: 第1阶段自测题
**布局**: 3个问题卡片
**内容**:
- ❓ 能画出DDD四层架构及依赖方向吗?
- ❓ 能说出Spring Boot和Vue的版本号吗?
- ❓ 知道本地开发环境如何一键启动吗?
**视觉**: 问号图标,互动式复选框样式

---

### 第三部分:第2阶段 - 核心业务流程 (5页)

#### Slide 8: JWT登录认证流程
**布局**: 流程图 + 关键机制说明
**内容**:
- 上方: 嵌入04_login_authentication_flow.svg图表
- 下方关键机制:
  - 双Token: Access(2小时) + Refresh(7天)
  - 自动续期: 剩余时间<30分钟触发刷新
  - 安全防护: IP限流、登录保护、防重放(时间戳+Nonce)
**实践建议**: Postman测试 POST /api/v1/auth/login
**视觉**: 流程箭头清晰,关键节点高亮

#### Slide 9: RBAC权限校验流程
**布局**: 三层权限模型图 + 数据模型
**内容**:
- 上方: 嵌入05_permission_check_flow.svg图表
- 三层权限:
  1. JWT认证(Token验证)
  2. RBAC校验(@RequirePermission)
  3. 数据权限(@DataScope)
- 数据模型: 5张表(user/role/menu/user_role/role_menu)
- 缓存策略: Caffeine L1(60秒TTL) + Redis L2
**实践建议**: 查看sys_menu表的perms字段
**视觉**: 层次结构,数据库ER关系图

#### Slide 10: 三级审批工作流
**布局**: 工作流状态机 + 高级功能
**内容**:
- 左侧: 嵌入06_workflow_approval_flow.svg图表
- 右侧: 审批流程
  - 发起请假 → 部门主管审批 → 分管领导审批 → 总经理审批 → 完成
- 高级功能: 回退、转办、委派、挂起、超时处理
- 数据模型: 4张表(flow_definition/instance/task/history)
**实践建议**: 管理后台创建请假流程体验
**视觉**: 状态机图,操作按钮示意

#### Slide 11: 文件分片上传流程
**布局**: 技术流程图 + 关键技术点
**内容**:
- 上方: 嵌入07_file_upload_flow.svg图表
- 关键技术:
  - 5MB分片策略(并发上传)
  - MD5秒传机制(文件哈希对比)
  - 断点续传(Redis记录已上传分片)
  - MinIO对象存储(可切换TOS/Local)
**实践建议**: 上传100MB文件观察Network面板
**视觉**: 流程图+代码片段

#### Slide 12: 第2阶段自测题
**布局**: 3个问题卡片
**内容**:
- ❓ 能说出Access Token和Refresh Token的有效期吗?
- ❓ 知道权限点命名规范(如system:user:list)吗?
- ❓ 理解工作流的回退和转办的区别吗?
**视觉**: 问号图标,互动式复选框样式

---

### 第四部分:第3阶段 - 基础设施与安全 (3页)

#### Slide 13: 数据库与缓存
**布局**: 2×2网格图表展示
**内容**:
- 左上: 08_database_er_diagram.svg(缩略)
  - 40+张表,Flyway V1-V26迁移
- 右上: 09_multi_level_cache_architecture.svg
  - L1 Caffeine(10k/5分钟/微秒级)
  - L2 Redis(7天TTL/1-5ms)
  - 命中率80-95%
- 左下: 表设计规范(字符集/索引/租户隔离)
- 右下: 防护机制(防击穿/穿透/雪崩)
**视觉**: 四象限布局,图表+文字说明

#### Slide 14: 限流熔断与数据权限
**布局**: 上下双图表
**内容**:
- 上方: 10_rate_limit_circuit_breaker.svg
  - Sentinel: @RateLimiter/@HotSpotRateLimit/@UserRateLimit
  - Resilience4j: 三档熔断策略(默认/严格/宽松)
- 下方: 11_data_permission_filter.svg
  - 5种数据范围: ALL/DEPT/DEPT_AND_CHILD/SELF/CUSTOM
  - @DataScope AOP拦截 + MyBatis SQL注入
**视觉**: 流程图+配置代码示例

#### Slide 15: 安全防护体系
**布局**: 7层防护架构图
**内容**:
- 中心: 12_security_protection_system.svg
- 7层防护:
  1. @AntiReplay 防重放(时间戳+Nonce)
  2. @IdempotentApi 接口幂等(幂等键)
  3. 输入校验(JSR-303 + 业务规则)
  4. XSS防护(输入过滤 + 响应头)
  5. SQL注入防护(预编译 + 输入校验)
  6. 扫描流量拦截(恶意路径 + UA检查)
  7. 审计日志(操作/登录/API/运行时)
**视觉**: 洋葱架构图,层层防护

---

### 第五部分:实践任务与考核 (3页)

#### Slide 16: 实践任务
**布局**: 3个任务卡片
**内容**:
- 任务1: 本地环境搭建(15分钟)
  ```bash
  docker compose up -d
  open http://localhost:8080/doc.html
  open http://localhost:3000  # Grafana
  ```
- 任务2: 创建第一个接口(30分钟)
  - 按DDD四层创建 GET /api/v1/products/{id}
  - Domain → Infrastructure → Application → Interfaces
- 任务3: 查看监控大盘(10分钟)
  - Grafana面板: QPS/P99/错误率/缓存命中率
**视觉**: 代码块高亮,终端命令样式

#### Slide 17: 培训考核清单
**布局**: 6项考核标准
**内容**:
- [ ] 能独立启动本地开发环境
- [ ] 能按照DDD四层架构创建一个CRUD接口
- [ ] 能在Grafana中查看监控指标
- [ ] 能解释JWT双Token机制和自动续期流程
- [ ] 能说出项目使用的核心技术栈及版本号
- [ ] 能找到特定技术问题对应的架构图表
**视觉**: 复选框列表,互动式设计

#### Slide 18: 结束页与学习资源
**布局**: 居中内容 + 底部资源链接
**内容**:
- 主标题: "恭喜完成培训! 🎉"
- 副标题: "现在你已具备独立开发能力"
- 学习资源:
  - 📖 ARCHITECTURE_INDEX.md - 架构文档索引
  - 📊 diagrams/README.md - SVG图表使用指南
  - 📝 standards/backend-development-standards.md - 后端开发规范
  - 💬 企业微信 Apex 开发群 - 获取帮助
- 版权: © 2026 Apex Team

---

## 🎨 视觉设计规范

### 配色方案
- **主色**: #667eea(靛蓝紫)
- **渐变**: linear-gradient(135deg, #667eea, #764ba2)
- **强调色**: #f093fb(粉紫), #4facfe(蓝), #43e97b(绿)
- **文字**: #1d1d1f(标题), #6e6e73(正文), #86868b(次要)
- **背景**: #ffffff(卡片), #f8f9ff(浅蓝背景)

### 字体规范
- **标题**: 56-72px, 粗体700
- **副标题**: 32-48px, 粗体600
- **正文**: 18-24px, 常规400
- **代码**: 16px, 等宽字体

### 布局规范
- **幻灯片尺寸**: 1600×900px (16:9)
- **内边距**: 80px(上下) × 120px(左右)
- **间距**: 24-48px(元素间距)
- **圆角**: 12px(卡片), 90px(圆形徽章)

---

## 📦 交付清单

1. **HTML文件**: `架构图表新人培训.html` - 完整交互式PPT
2. **图片资源**: `images/` 文件夹 - 所有架构图表SVG
3. **大纲文档**: 本文档 - 便于后续修改和扩展
4. **使用说明**:
   - `F` 键进入全屏演示模式
   - `Space` / `→` 下一页
   - `←` 上一页
   - `Esc` 退出全屏

---

## 🔄 后续扩展建议

1. **添加动画**: 考虑使用reveal.js等库添加页面切换动画
2. **互动元素**: 添加点击展开的详细信息面板
3. **视频嵌入**: 关键流程可录制演示视频嵌入
4. **导出PDF**: 使用浏览器打印功能导出为PDF
5. **多语言版本**: 制作英文版培训课件

---

**文档维护者**: 架构团队
**版本**: v1.0
**生成日期**: 2026-09-10
**基于文档**: ONBOARDING_ARCHITECTURE_GUIDE.md

---

© 2026 Apex Team · 帮助新人快速融入团队,理解项目架构

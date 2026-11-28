# BE-067~070 跟进修复联合规格（2026-11-28）

> 来源：BE-066 全仓对齐战役（`docs/records/2026-11-28-be066-alignment-campaign.md`）评审发现的四项代码层缺陷。
> R0/执行：ZCode（用户 2026-11-28 授权修复，串行单写者）；基线 `dev@07a93cbf`；独立 R5 复核后再集成。
> 关联任务卡：BE-067 / BE-068 / BE-069 / BE-070（本轮新增 BE-071，见 §6）。

## 1. BE-067 system：interests 死白名单与死限流规则清理

### 1.1 现状与证据

- 全仓无任何 Router/Handler 注册 `/internal/v1/interests/**` 与 `/api/v1/same-frequency/interests/feed`（rg 全仓 0 注册）。
- 安全链残留三处特判：
  - `SecurityConfig.java:183、191`：rbac / 非 rbac 两分支各一条 interests 路径 `hasAnyRole` / `authenticated()` 匹配器；
  - `JwtAuthenticationFilter.java:298-299`：`forceFreshRoles` 对 interests 前缀强制绕过角色缓存；该旗标沿 `getUserAuthorities(…, boolean)` → `materializeUserContext(…, boolean)` 传递，仅本地缓存回退分支（`userRolesCache.getIfPresent`）消费；认证中心分支恒传 `false`；统一授权分支完全忽略该旗标；
  - `SentinelConfig.java:376`：`/api/v1/same-frequency/interests/feed` 流控规则（同频组共 7 条，其余 6 条路径亦无路由注册，属更大范围的遗留基线，另立 BE-071，不在本卡范围）。
- 测试引用：`JwtAuthenticationFilterTest.filter_withInterestGovernancePath_shouldBypassRoleCache`（断言同路径两次请求各实时读仓储一次）；`BusinessFlowE2EIntegrationTest` 中的 "interests" 为问卷选项 ID，与路径无关。

### 1.2 改动清单

1. `SecurityConfig`：两分支各删除 interests 匹配器（该前缀随后落入 `anyExchange().authenticated()` 默认口径，无路由即 404，不产生放权）。
2. `JwtAuthenticationFilter`：删除 `forceFreshRoles` 计算与布尔参数链（`getUserAuthorities` / `materializeUserContext` 移除参数，调用点同步；本地缓存回退分支恒走缓存优先），方法 Javadoc（§57/§64/§420/§522）同步删除"敏感路径强制刷新"表述。
3. `SentinelConfig`：删除 :376 一条流控规则；类头 Javadoc 同频组计数 7→6（兴趣子路径描述保留，因 interest-universe/channels/connections 三条规则仍在）。
4. 测试：删除 `filter_withInterestGovernancePath_shouldBypassRoleCache`，新增反转断言回归 `filter_withInternalInterestsPath_shouldUseCachedRoles`——同路径两次请求仅 `findByUserId` 1 次（第二次命中真实 Caffeine 缓存），证明死白名单移除后恢复常规缓存链路。

### 1.3 影响与回滚

- 行为变化：仅对已不存在的路径；存量合法路由的认证/授权语义零变化（forceFreshRoles 在现存路径上本就为 false）。
- 回滚：`git revert` 单笔提交。

## 2. BE-068 aggregation：todo 配置前缀死键修正

### 2.1 现状与证据

`apex-aggregation-service/src/main/resources/application.yml:30` 起的 `aggregation.todo.*` 块（sources/page-size/approval-limit/message-limit/timeout-millis/bulkhead/default-bulkhead-max-concurrency）挂在 `aggregation:` 顶层键下，而 `TodoProperties` 绑定前缀为 `apex.todo`（`TodoProperties.java:36`）——整块不被绑定，运行默认来自代码（sources 8093/8084、page-size 10、approval-limit 10、message-limit 10、timeout 3000、bulkhead 默认 8），恰与 yml 字面值一致，故此前未暴露行为差异。`aggregation.security.*` 有独立绑定（`AggregationSecurityConfigTest` 在位），保留不动。

### 2.2 改动清单

1. yml：将 `todo:` 子块从 `aggregation:` 迁至既有顶层 `apex:` 键下（`apex.todo.*`），注释不变。
2. 新增 `TodoPropertiesYmlBindingTest`：用 `YamlPropertySourceLoader` 读取 classpath `application.yml`，经 `Binder` 绑定 `TodoProperties`，断言 sources.workflow/office、page-size=10、approval-limit=10、message-limit=10、timeout-millis=3000、default-bulkhead-max-concurrency=8 均从 yml 实际绑定（防止前缀再度漂移）。

### 2.3 影响与回滚

- 行为变化：无（yml 字面值与代码默认一致，仅使配置真正生效、可被环境变量覆盖路径打通）。
- 回滚：`git revert` 单笔提交。

## 3. BE-069 system：prod 模板管理端口默认与 8083 惯例对齐

### 3.1 现状与证据

- 共享 prod 模板 `apex-components-parent/apex-system/src/main/resources/application-prod.yml:443`：`management.server.port: ${MANAGEMENT_SERVER_PORT:8081}`，注释「管理端口与业务端口（8080）物理隔离」——**8080/8081 为退役单体口径**（单体业务端口即 8080），而 system-service 现行业务端口 8082、管理端口 8083（模块 `application.yml` 284、compose 端口映射与 healthcheck、k8s 探针、全部文档一致）。
- 生效链：compose `SPRING_PROFILES_ACTIVE=prod,enterprise` → 共享 prod 模板（profile 专属）覆盖模块 `application.yml` 的 `port: 8083` → **默认 8081**；compose 未注入 `MANAGEMENT_SERVER_PORT`（注入的 `MANAGEMENT_PORT`/`HEALTHCHECK_PORT` 为 Containerfile 构建参数，`HEALTHCHECK_PORT` 仅用于镜像 HEALTHCHECK 指令，运行期无 Java 消费方）→ **容器内 actuator 实际绑定 8081，而容器 HEALTHCHECK 与端口映射均指向 8083**，属真实缺陷（本地 `.env` 若注入 `MANAGEMENT_SERVER_PORT=8083` 可掩盖）。
- k8s `deployment.yaml`（system 块）显式注入 `MANAGEMENT_SERVER_PORT=8083` 且探针 8083——显式 env 覆盖下模板默认不生效，改默认值对 k8s 无影响。

### 3.2 改动清单

1. 共享 prod 模板：`port: ${MANAGEMENT_SERVER_PORT:8083}`，注释改为「与业务端口 8082 物理隔离；默认 8083，可通过 MANAGEMENT_SERVER_PORT 调整」。
2. 文档同步：两层取舍说明（profile 专属 prod 模板覆盖模块 application.yml；k8s 显式注入、compose 依赖模板默认）。**执行勘误（R5 N-01）**：实际落在 `docs/05-operations/deployment.md` §7.6 `MANAGEMENT_SERVER_PORT` 变量登记处（configuration.md 无该条目承载位），落点已核验合理，同一结论不设双正文。

### 3.3 影响与回滚

- 行为变化：仅 prod 档且未显式注入 `MANAGEMENT_SERVER_PORT` 的部署（本地 `.env` 有注入的部署零变化；java -jar prod 部署若依赖 8081 需在升版说明中显式声明——本仓 compose/k8s 两套官方编排均按 8083 口径）。
- 回滚：`git revert` 单笔提交。

## 4. BE-070 仓库根：`monitoring/` 重复目录清理

### 4.1 现状与证据

根级 `monitoring/`（alert-rules.yml、alertmanager.yml、podman-compose.yml、prometheus.yml、grafana/）与权威目录 `config/monitoring/` 内容重复且结构不一致（后者含 README、alertmanager/、alloy、elk 等完整栈）。全仓引用扫描（含 scripts/、.github、Containerfile、全部活文档）**零处指向根级目录**（命中均为 `config/monitoring/…` 与 `scripts/ops/monitoring/…` 自身路径）；归档区（docs/archived、docs/_archive）豁免不计。

### 4.2 改动清单

`git rm -r monitoring/`（**7 个已跟踪文件**，规格初稿误计 6 个——勘误见 R5 N-02），`docs/records/` 留痕。删除已由用户 2026-11-28 指令确认（本卡破坏性操作确认条件满足）。**引用面修正（R5 MF-01）**：`docs/diagrams/13_docker_compose_local_dev.svg:54-55` 存在 1 处对根级目录的**惰性文本引用**（活图，登记于主索引；脚本/CI/运行时零引用已实证，删除安全）；该图随 BE-071 范围按 §0.3 出 `_v2` 修订。

### 4.3 影响与回滚

- 行为变化：无（零引用）。
- 回滚：`git revert` 恢复目录。

## 5. 验证命令与门禁

```bash
source scripts/build/init-build-env.sh
mvnd -pl :apex-system-service -am test          # BE-067 安全链回归 + 既有用例
mvnd -pl :apex-aggregation-service -am test     # BE-068 绑定断言 + 既有用例
bash scripts/governance/verify-docs.sh --strict # 文档同步后
bash scripts/ci/check-doc-links.sh              # 0 断链
```

## 6. 本轮新增工单

- **BE-071（P2，待规划）**：SentinelConfig 静态基线全量审计——43 条流控规则中初步扫描显示大部分路径（同频组其余 6 条、media/ranking/explore/future-partner/moderation/onboarding/profile 等退役业务域组）无路由注册，且 `/api/v1/auth/**` 组指向 auth-service 现行前缀 `/auth/v1/**` 以外的旧前缀；需逐条对照现行网关路由表与各服务 Router 出证据后删改，并重写类头 Javadoc 分组表；不得在逐条证据前批量删除。

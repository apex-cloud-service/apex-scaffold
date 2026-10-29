# Apex Scaffold 商业化售卖落地执行计划

> **文档版本**: v1.0  
> **编制日期**: 2026-09-09  
> **项目版本**: v1.2.0  
> **就绪度评分**: 92/100  
> **执行周期**: 4周（20个工作日）

---

## 📋 执行摘要

### 当前状态

Apex Scaffold v1.2.0 已通过商业化就绪度评审（92/100），具备以下商业化基础：

| 维度 | 状态 | 说明 |
|------|------|------|
| 三档版本 | ✅ 已实现 | Basic(免费)/Standard(¥9,999)/Enterprise(¥49,999) |
| License机制 | ✅ 已实现 | RSA签名 + 机器指纹绑定 + FeatureGate门控 |
| 商业协议 | ✅ 已就绪 | LICENSE-COMMERCIAL.md 双轨授权 |
| 发布脚本 | ✅ 已就绪 | scripts/release/release.sh 三档打包 |
| License发放 | ✅ 已就绪 | scripts/license/generate-license.sh |
| 销售材料 | ✅ 已就绪 | docs/sales/ 4份文档 |
| 部署工具 | ✅ 已就绪 | Podman Compose + K8s + 备份恢复 |
| 代码生成器 | ✅ 已就绪 | DDD五层 + Vue3前端模板 |

### 执行目标

**4周内完成全部商业化售卖准备工作，实现可在线交付、可合法售卖、可持续运营。**

---

## 📅 总体时间线

```
Week 1: 发布准备 ─── 产物打包 + License体系 + 文案定稿
Week 2: 渠道搭建 ─── GitHub仓库 + 官网页面 + 支付接入
Week 3: 交付体系 ─── 客户文档 + 交付流程 + 技术支持
Week 4: 上线运营 ─── 试售卖 + 反馈收集 + 正式发布
```

---

## 第一周：发布准备（5个工作日）

### Day 1-2: 发布产物打包验证

#### 1.1 三档版本构建验证

**目标**: 确保三档版本打包正确，功能差异真实生效

**执行步骤**:
```bash
# 1. Basic版构建（验证依赖裁剪）
./scripts/release/release.sh basic 1.2.0
# 验证: jar中不含RocketMQ/Sentinel/Zipkin依赖

# 2. Standard版构建
./scripts/release/release.sh standard 1.2.0
# 验证: 含缓存/监控/RBAC，不含MQ/链路追踪

# 3. Enterprise版构建
./scripts/release/release.sh enterprise 1.2.0
# 验证: 含全部功能
```

**验收标准**:
- [ ] Basic版jar不含高级依赖（`jar tf | grep rocketmq` 返回空）
- [ ] Standard版jar含Sentinel不含RocketMQ
- [ ] Enterprise版jar含全部依赖
- [ ] 三档版本启动均正常
- [ ] 三档版本功能差异符合COMMERCIAL_VERSIONS.md矩阵

**产出物**:
```
dist/
├── apex-scaffold-1.2.0-basic.zip
├── apex-scaffold-1.2.0-standard.zip
└── apex-scaffold-1.2.0-enterprise.zip
```

#### 1.2 交付包内容验证

**每个zip包应包含**:
- `apex-*.jar` — 可执行jar
- `LICENSE` / `LICENSE-COMMERCIAL.md` — 许可证文件
- `docs/` — 技术文档
- `config/` — 配置模板 + 公钥
- `podman-compose.yml` / `Containerfile` — 容器化
- `k8s/` — K8s部署清单（仅Enterprise）
- `scripts/` — 运维脚本

**验证清单**:
- [ ] 私钥（`private.key`）不在交付包中
- [ ] License发放工具（`apex-license-tools`）不在交付包中
- [ ] 公钥（`public.key`）在交付包中
- [ ] 各版本README指引正确

### Day 2-3: License体系验证

#### 2.1 密钥管理

**目标**: 确保签发私钥安全、公钥正确分发

**执行步骤**:
```bash
# 1. 检查密钥对是否已生成
ls -la config/license/
# 期望: public.key 存在，private.key 存在但已gitignore

# 2. 验证私钥未入库
git ls-files config/license/private.key
# 期望: 无输出

# 3. 备份私钥到安全位置（离线存储）
cp config/license/private.key ~/secure-vault/apex-private-$(date +%Y%m%d).key
```

**验收标准**:
- [ ] `private.key` 未被git跟踪
- [ ] `private.key` 已离线备份
- [ ] `public.key` 在交付包config目录中
- [ ] `.gitignore` 包含 `config/license/private.key`

#### 2.2 License发放流程验证

**目标**: 验证端到端License发放流程

**执行步骤**:
```bash
# 1. 模拟发放标准版License（30天试用）
./scripts/license/generate-license.sh STANDARD "测试客户" 30

# 2. 模拟发放企业版License（带机器指纹绑定）
./scripts/license/generate-license.sh ENTERPRISE "测试客户" 365 --fingerprint test123

# 3. 验证License文件格式
cat dist/license/standard-30d-*.json | python3 -m json.tool
```

**验收标准**:
- [ ] License文件正确生成
- [ ] License包含正确的功能集
- [ ] 机器指纹绑定生效
- [ ] License过期后功能自动降级

#### 2.3 FeatureGate门控验证

**目标**: 验证商业功能在无License时正确降级

**验证场景**:
| 场景 | 期望行为 |
|------|----------|
| 无License文件 | 商业功能降级，基础功能正常 |
| 过期License | 商业功能降级，基础功能正常 |
| 有效Standard License | Standard功能启用，Enterprise功能降级 |
| 有效Enterprise License | 全部功能启用 |
| 伪造License | 商业功能降级 + 日志告警 |

### Day 3-4: 文案与材料定稿

#### 3.1 销售材料审核

**现有材料**（docs/sales/）:
- `PERFORMANCE_REPORT.md` — 性能报告
- `SECURITY_WHITEPAPER.md` — 安全白皮书
- `API_SPECIFICATION.md` — API规格说明
- `DELIVERY_CHECKLIST.md` — 交付清单

**审核清单**:
- [ ] 性能数据是否最新（基于v1.2.0重新测试）
- [ ] 安全白皮书是否覆盖最新安全特性
- [ ] API文档是否与代码一致
- [ ] 交付清单是否完整

#### 3.2 定价页面文案

**目标**: 完成[COMMERCIAL_VERSIONS.md](COMMERCIAL_VERSIONS.md)的最终审核

**审核要点**:
- [ ] 三档价格清晰（Basic免费/Standard ¥9,999年/Enterprise ¥49,999年）
- [ ] 永久授权价格明确（Standard ¥29,999/Enterprise ¥149,999）
- [ ] 功能矩阵与实现一致
- [ ] 升级路径说明清晰
- [ ] FAQ覆盖常见疑问

#### 3.3 README.md商业化优化

**目标**: 确保GitHub首页能有效转化访客

**优化要点**:
- [ ] 项目简介突出差异化（WebFlux + DDD + 响应式）
- [ ] 快速开始指南5分钟可运行
- [ ] 三档版本对比表格
- [ ] 架构图清晰专业
- [ ] 链接到详细文档

### Day 5: 发布前最终检查

#### 5.1 全量回归测试

```bash
# 编译验证
mvn clean compile -q

# 单元测试
mvn test -q

# 集成测试（如有）
mvn verify -Pintegration -q
```

**验收标准**:
- [ ] 编译零错误
- [ ] 单元测试通过率100%
- [ ] 无PMD/P3C严重告警
- [ ] ArchUnit分层测试通过

#### 5.2 商业化就绪度检查

```bash
./scripts/utils/check-commercialization-readiness.sh
```

**验收标准**:
- [ ] 评分 ≥ 90/100
- [ ] 无失败项

#### 5.3 交付包签名

```bash
# 为每个交付包生成SHA-256校验和
cd dist/
sha256sum apex-scaffold-1.2.0-*.zip > checksums.txt
```

---

## 第二周：渠道搭建（5个工作日）

### Day 6-7: GitHub仓库准备

#### 6.1 仓库整理

**目标**: 准备公开的GitHub仓库（Basic版引流）

**执行步骤**:
- [ ] 创建新的公开仓库 `apex-scaffold`（或使用现有仓库）
- [ ] 确保仓库中不含业务代码和敏感数据
- [ ] 确保仓库中不含私钥和License发放工具
- [ ] 添加GitHub Topics: `spring-boot` `webflux` `ddd` `scaffold` `java`
- [ ] 配置README.md为首页展示

#### 6.2 GitHub Release发布

**执行步骤**:
```bash
# 1. 创建v1.2.0 tag
git tag -a v1.2.0 -m "Release v1.2.0 - Commercial Ready"

# 2. 推送tag
git push origin v1.2.0

# 3. 创建GitHub Release
gh release create v1.2.0 \
  --title "Apex Scaffold v1.2.0" \
  --notes "首个商业化版本" \
  dist/apex-scaffold-1.2.0-basic.zip \
  dist/checksums.txt
```

**验收标准**:
- [ ] GitHub Release页面可访问
- [ ] Basic版zip可下载
- [ ] Release Notes清晰

#### 6.3 Issue模板和Wiki

- [ ] 创建Issue模板（Bug报告/功能请求/技术咨询）
- [ ] 创建Wiki首页（项目介绍 + 快速开始 + 文档索引）
- [ ] 配置Discussions开启（社区交流）

### Day 7-8: 官网页面搭建

#### 7.1 产品官网

**目标**: 搭建简单的产品介绍页（可用GitHub Pages）

**页面结构**:
```
首页
├── 产品介绍（WebFlux + DDD + 响应式 + 监控）
├── 功能特性（三档对比表）
├── 快速开始（5分钟体验）
├── 定价方案
├── 文档中心
│   ├── 快速开始
│   ├── 架构设计
│   ├── 部署指南
│   ├── API文档
│   └── 最佳实践
├── 在线购买
└── 技术支持
```

**技术方案**: GitHub Pages + Vue3静态页面 或 简单HTML

**验收标准**:
- [ ] 官网可访问
- [ | 三档对比表清晰
- [ ] 文档链接正确
- [ ] 购买引导明确

#### 7.2 在线购买流程

**目标**: 实现客户可在线完成购买

**流程设计**:
```
客户访问定价页 → 选择版本 → 填写信息 → 支付 → 
收到License文件 + 下载链接 → 交付完成
```

**实现方案**（MVP阶段）:
1. 客户填写购买表单（Google Form或自建表单）
2. 人工确认后发送支付链接（支付宝/微信/对公转账）
3. 收到付款后人工签发License
4. 发送License文件 + 下载链接给客户

**后续优化**（自动化）:
1. 接入支付网关（支付宝/微信支付）
2. 自动签发License
3. 自动发送下载链接

### Day 9: 支付与合同流程

#### 9.1 支付方式准备

**支持方式**:
- [ ] 支付宝收款码/企业账户
- [ ] 微信支付收款码/企业账户
- [ ] 对公转账信息（公司名称、银行、账号）
- [ ] PayPal（面向海外客户）

#### 9.2 合同模板

**目标**: 准备标准化的商业授权合同

**合同要素**:
- 授权方信息
- 被授权方信息
- 授权版本（Standard/Enterprise）
- 授权期限（1年/永久）
- 部署实例数限制
- 技术服务条款
- 保密条款
- 违约责任
- 签字盖章

**验收标准**:
- [ ] 合同模板经法务审核
- [ ] 与LICENSE-COMMERCIAL.md条款一致

### Day 10: 渠道就绪验证

**全链路验证**:
- [ ] GitHub仓库可访问，Basic版可下载
- [ ] 官网页面可访问，信息准确
- [ ] 购买表单可提交
- [ ] 支付方式可用
- [ ] License可签发
- [ ] 交付包可下载

---

## 第三周：交付体系（5个工作日）

### Day 11-12: 客户文档完善

#### 11.1 客户交付文档

**目标**: 客户购买后能独立完成部署

**文档清单**:

| 文档 | 目标读者 | 核心内容 |
|------|----------|----------|
| 快速开始指南 | 开发者 | 5分钟Podman启动 |
| 部署指南 | 运维工程师 | Podman/K8s部署 |
| License管理指南 | 运维工程师 | License安装/更新/续期 |
| 配置说明 | 开发者 | 各配置项说明 |
| API文档 | 开发者 | 接口列表+示例 |
| 最佳实践 | 架构师 | DDD/缓存/MQ使用 |
| 升级指南 | 开发者 | 版本升级步骤 |
| 故障排查 | 运维工程师 | 常见问题解决 |

**验收标准**:
- [ ] 每篇文档有目标读者标注
- [ ] 步骤可操作、可验证
- [ ] 截图/示例代码准确

#### 11.2 交付Checklist

**目标**: 标准化交付流程

**交付Checklist**:
```
□ 1. License文件已签发并发送
□ 2. 交付包下载链接已发送
□ 3. SHA-256校验和已提供
□ 4. 部署指南已提供
□ 5. License管理指南已提供
□ 6. 技术支持联系方式已提供
□ 7. 售后期限已说明
□ 8. 升级政策已说明
```

### Day 12-13: 技术支持体系

#### 12.1 支持渠道搭建

**支持矩阵**:

| 版本 | 支持渠道 | 响应时间 | 服务时间 |
|------|----------|----------|----------|
| Basic | GitHub Issues | 社区支持 | - |
| Standard | 邮件支持 | 5工作日 | 9x5 |
| Enterprise | 专属客服 | 2小时 | 7x24 |

**搭建任务**:
- [ ] 创建技术支持邮箱（support@apex-scaffold...）
- [ ] 创建商务咨询邮箱（sales@apex-scaffold...）
- [ ] 配置GitHub Issue模板
- [ ] 准备常见问题知识库

#### 12.2 知识库建设

**目标**: 沉淀常见问题，减少重复咨询

**初始知识库内容**:
- [ ] 环境要求（JDK/MySQL/Redis版本）
- [ ] 启动失败常见原因
- [ ] License安装指南
- [ ] Podman部署FAQ
- [ ] 数据库初始化FAQ
- [ ] 功能开关配置FAQ

### Day 14: 版本发布与维护策略

#### 14.1 版本发布策略

**发布节奏**:

| 版本 | 频率 | 内容 |
|------|------|------|
| Basic | 季度 | Bug修复 + 基础功能 |
| Standard | 月度 | Bug修复 + 新功能 + 优化 |
| Enterprise | 每周 | 紧急补丁 + 持续优化 |

#### 14.2 变更日志规范

**格式**: Keep a Changelog标准

```markdown
## [1.2.0] - 2026-09-09

### Added
- 新增多级缓存击穿防护
- 新增License机器指纹绑定

### Changed
- 升级Spring Boot至3.5.15

### Fixed
- 修复JsonUtil反序列化缺陷

### Security
- 修复JWT密钥校验绕过风险
```

#### 14.3 升级政策

- [ ] Minor版本升级免费（1.2.0 → 1.2.1）
- [ ] Major版本升级免费（授权期内）
- [ ] 授权到期后可继续使用当前版本
- [ ] 续期费用为原价的60%

### Day 15: 交付体系验收

**全链路演练**:
1. 模拟客户填写购买表单
2. 模拟签发License
3. 模拟交付包发送
4. 模拟客户部署安装
5. 模拟技术支持响应

**验收标准**:
- [ ] 全流程可在1小时内完成
- [ ] 客户可独立完成部署
- [ ] 技术支持响应及时

---

## 第四周：上线运营（5个工作日）

### Day 16-17: 试售卖

#### 16.1 内部测试售卖

**目标**: 邀请3-5位内部/友好用户完成购买流程

**测试场景**:
- [ ] 场景1: 购买Standard版年付
- [ ] 场景2: 购买Enterprise版永久授权
- [ ] 场景3: 试用Standard版30天
- [ ] 场景4: Basic版GitHub下载

**收集反馈**:
- [ ] 购买流程是否顺畅
- [ ] 交付包是否完整
- [ ] 部署是否顺利
- [ ] 文档是否清晰
- [ ] 支持是否及时

#### 16.2 问题修复

- [ ] 根据试售卖反馈修复问题
- [ ] 更新文档
- [ ] 优化流程

### Day 18: 正式上线

#### 18.1 发布公告

**渠道**:
- [ ] GitHub Release正式发布
- [ ] 技术社区发帖（V2EX/掘金/SegmentFault/CSDN）
- [ ] 微信公众号/技术群推广
- [ ] 知乎专栏文章

**公告内容要点**:
- 产品定位（WebFlux响应式 + DDD企业级脚手架）
- 核心优势（66个监控面板 + 三高能力 + License保护）
- 三档定价
- 快速体验方式（Podman一键启动）
- 购买方式

#### 18.2 上线监控

**监控指标**:
- [ ] GitHub Star数
- [ ] GitHub Release下载量
- [ ] 官网访问量
- [ ] 购买咨询量
- [ ] 实际成交量

### Day 19-20: 运营优化

#### 19.1 数据分析

**关键指标**:
- 转化率: 访客 → 咨询 → 购买
- 客户来源: GitHub/社区/搜索/推荐
- 客户画像: 个人开发者/创业公司/中型企业/大型企业
- 购买偏好: Basic/Standard/Enterprise

#### 19.2 持续优化

**优化方向**:
- [ ] 根据客户反馈优化文档
- [ ] 根据常见问题丰富知识库
- [ ] 根据销售数据调整定价策略
- [ ] 根据技术支持趋势优化产品

---

## 📊 里程碑与验收

### 里程碑节点

| 里程碑 | 日期 | 验收标准 | 状态 |
|--------|------|----------|------|
| M1: 发布准备完成 | Week 1末 | 三档产物打包通过、License体系验证通过 | 待执行 |
| M2: 渠道搭建完成 | Week 2末 | GitHub/官网/支付渠道全链路可用 | 待执行 |
| M3: 交付体系就绪 | Week 3末 | 文档完整、支持渠道可用、全链路演练通过 | 待执行 |
| M4: 正式上线 | Week 4中 | 公开发布、首批客户成交 | 待执行 |

### 关键交付物

| 序号 | 交付物 | 负责人 | 截止日 |
|------|--------|--------|--------|
| 1 | 三档版本zip包 | 技术 | Day 2 |
| 2 | License体系验证报告 | 技术 | Day 3 |
| 3 | 销售材料定稿 | 产品 | Day 4 |
| 4 | GitHub Release | 技术 | Day 7 |
| 5 | 产品官网 | 产品 | Day 8 |
| 6 | 支付渠道 | 运营 | Day 9 |
| 7 | 客户文档体系 | 技术 | Day 12 |
| 8 | 技术支持体系 | 运营 | Day 13 |
| 9 | 试售卖报告 | 产品 | Day 17 |
| 10 | 正式发布公告 | 市场 | Day 18 |

---

## 🎯 定价与收入预测

### 定价策略

| 版本 | 年付价格 | 永久授权 | 目标客户 |
|------|----------|----------|----------|
| Basic | 免费 | - | 个人开发者/引流 |
| Standard | ¥9,999 | ¥29,999 | 中型企业/创业公司 |
| Enterprise | ¥49,999 | ¥149,999 | 大型企业/强监管行业 |

### 收入预测（保守估计）

| 时间段 | Standard | Enterprise | 合计 |
|--------|----------|------------|------|
| 第1月 | 2单 (¥19,998) | 0单 | ¥19,998 |
| 第2月 | 3单 (¥29,997) | 1单 (¥49,999) | ¥79,996 |
| 第3月 | 5单 (¥49,995) | 1单 (¥49,999) | ¥99,994 |
| 季度合计 | 10单 (¥99,990) | 2单 (¥99,998) | **¥199,988** |
| 年度预测 | 30单 (¥299,970) | 8单 (¥399,992) | **¥699,962** |

### 成本估算

| 项目 | 成本 |
|------|------|
| 服务器/域名 | ¥3,000/年 |
| 支付手续费 | 收入的1% |
| 法务/合同 | ¥5,000（一次性） |
| 推广营销 | ¥10,000/年 |
| **年度总成本** | **约¥25,000** |
| **年度净利润** | **约¥675,000** |
| **ROI** | **2700%** |

---

## ⚠️ 风险管理

### 风险矩阵

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|----------|
| License被破解 | 低 | 高 | RSA签名 + 机器指纹 + 法律手段 |
| 竞品免费模仿 | 高 | 中 | 持续创新 + 企业版差异化 |
| 客户需求定制化 | 中 | 中 | 标准版不定制，企业版收费定制 |
| 技术支持压力 | 中 | 中 | 知识库 + 社区 + 付费优先 |
| 版本兼容问题 | 低 | 高 | 语义化版本 + 回归测试 |

### 应急预案

**License被破解**:
1. 更新公私钥对
2. 发布紧急安全版本
3. 对已签发License重新签发
4. 法律追究

**重大Bug**:
1. 24小时内发布补丁版本
2. 通知所有受影响客户
3. Enterprise客户提供远程协助

---

## 📋 执行检查清单

### Week 1 检查清单

- [ ] `./scripts/release/release.sh basic 1.2.0` 成功
- [ ] `./scripts/release/release.sh standard 1.2.0` 成功
- [ ] `./scripts/release/release.sh enterprise 1.2.0` 成功
- [ ] Basic版jar不含高级依赖
- [ ] `./scripts/license/generate-license.sh STANDARD "测试" 30` 成功
- [ ] License过期后功能正确降级
- [ ] 私钥未入库且已备份
- [ ] 销售材料审核通过
- [ ] 全量测试通过
- [ ] 商业化就绪度 ≥ 90分

### Week 2 检查清单

- [ ] GitHub仓库公开可访问
- [ ] GitHub Release发布成功
- [ ] Basic版可下载
- [ ] 官网页面可访问
- [ ] 三档对比表准确
- [ ] 购买表单可提交
- [ ] 支付方式可用
- [ ] 合同模板经法务审核

### Week 3 检查清单

- [ ] 8篇客户文档完成
- [ ] 交付Checklist可用
- [ ] 技术支持邮箱配置完成
- [ ] 知识库初始内容完成
- [ ] 全链路演练通过

### Week 4 检查清单

- [ ] 3+位试售卖用户反馈收集
- [ ] 反馈问题已修复
- [ ] 发布公告已准备
- [ ] 正式上线
- [ ] 首批客户成交
- [ ] 运营数据开始收集

---

## 📎 附录

### A. 已有资产清单

| 资产 | 位置 | 说明 |
|------|------|------|
| 三档版本配置 | pom.xml | Maven Profile + Spring条件门控 |
| License发放 | scripts/license/generate-license.sh | RSA签名 + 机器指纹 |
| 发布打包 | scripts/release/release.sh | 三档zip打包 |
| 备份恢复 | scripts/backup/ | MySQL备份恢复 |
| 代码生成器 | scripts/codegen/ | DDD五层+Vue3模板 |
| 项目初始化 | scripts/init/new-project.sh | 一键改名 |
| Podman部署 | podman-compose.yml | 应用栈 |
| 基础设施 | podman-compose.infra.yml | MySQL/Redis/MQ等 |
| K8s部署 | k8s/ | 部署+金丝雀 |
| 版本说明 | docs/07-commercial/COMMERCIAL_VERSIONS.md | 三档功能矩阵 |
| 商业协议 | LICENSE-COMMERCIAL.md | 授权条款 |
| License管理 | docs/LICENSE_MANAGEMENT.md | 安装/续期指南 |
| 销售材料 | docs/sales/ | 4份采购材料 |
| 就绪评审 | docs/COMMERCIAL_READINESS_REVIEW_2026-08.md | 92分评审报告 |
| 商业化就绪检查 | scripts/utils/check-commercialization-readiness.sh | 自动化检查 |

### B. 关键命令速查

```bash
# 构建
./scripts/build/build.sh

# 三档打包
./scripts/release/release.sh basic 1.2.0
./scripts/release/release.sh standard 1.2.0
./scripts/release/release.sh enterprise 1.2.0

# License发放
./scripts/license/generate-license.sh STANDARD "客户名" 365
./scripts/license/generate-license.sh ENTERPRISE "客户名" 365 --fingerprint <hash>

# 商业化检查
./scripts/utils/check-commercialization-readiness.sh

# 代码生成
./scripts/codegen/generate.sh

# 项目初始化
./scripts/init/new-project.sh

# 数据库备份
./scripts/backup/db-backup.sh
./scripts/backup/db-restore.sh
```

### C. 联系方式模板

**技术支持**:
- 邮箱: support@apex-scaffold.example.com
- GitHub Issues: https://github.com/xxx/apex-scaffold/issues
- 响应时间: Standard 5工作日 / Enterprise 2小时

**商务咨询**:
- 邮箱: sales@apex-scaffold.example.com
- 电话: 400-XXX-XXXX
- 支持对公转账、合同签署

**试用申请**:
- Standard版: 30天免费试用
- Enterprise版: 14天POC验证
- 联系销售获取试用License

---

**计划编制**: 开发团队
**审核状态**: 待确认  
**下一步**: 确认后从Week 1 Day 1开始执行

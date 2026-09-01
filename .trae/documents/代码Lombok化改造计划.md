# 代码 Lombok 化改造计划

## 一、摘要

将 apex-scaffold 项目存量 Java 代码中"纯样板"的手写 getter/setter 改用 Lombok 注解生成(以 `@Data` 为主),类名保持与文件一致,不改变现有类行为、API 与序列化语义。Lombok 1.18.36 已在父 POM 及各模块配置完毕(依赖 + 注解处理器路径),本次**仅改造存量 Java 源文件**,不改动 pom、不改动生成器模板、不改动已有 Lombok 注解的类。

## 二、背景与现状分析

### 2.1 Lombok 基础设施已就绪

- 父 POM [pom.xml](file:///Users/work/apex-scaffold/pom.xml) 声明 `lombok.version=1.18.36`(`dependencyManagement` L323、`maven-compiler-plugin` 注解处理器路径 L959-970、`spring-boot-maven-plugin` 打包排除 L990-998)。

- 全部 9 个模块的 pom 均已声明 `org.projectlombok:lombok`(provided 或随父依赖),无需新增任何依赖配置。

### 2.2 项目既有 Lombok 使用习惯(改造须对齐)

| 类别              | 现状                                                                                                             | 代表文件                                                                                                                                                                                                                                                                                                                                                 |
| --------------- | -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| DTO / PO / 请求响应 | 大量使用 `@Data`(约 117 个)                                                                                          | [PageRequest](file:///Users/work/apex-scaffold/apex-application/src/main/java/com/apex/scaffold/application/dto/PageRequest.java)(`@Data` + 手写逻辑 getter 共存)、各 `*PO`                                                                                                                                                                                  |
| 领域实体/值对象        | `@Getter`+`@EqualsAndHashCode`/`@ToString`,保留静态工厂与逻辑 getter                                                    | [TenantEntity](file:///Users/work/apex-scaffold/apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/TenantEntity.java)、[PhoneNumber](file:///Users/work/apex-scaffold/apex-domain/src/main/java/com/apex/scaffold/domain/model/valueobject/PhoneNumber.java)                                                                             |
| `@Data` 与手写方法共存 | `@Data` 下保留手写逻辑/compat 方法(Lombok 遇同名方法自动跳过)                                                                    | [BasePO.getLockVersion()](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/BasePO.java#L96-L98)、[ReportTicketPO.getEvidenceJson()](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/ReportTicketPO.java#L76-L87) |
| 结果/异常           | `@Getter`+`@Setter`+`@ToString`+`@EqualsAndHashCode`+`@NoArgsConstructor`+`@AllArgsConstructor`,链式 setter 手写保留 | [Result](file:///Users/work/apex-scaffold/apex-common/src/main/java/com/apex/scaffold/common/result/Result.java)、[BaseException](file:///Users/work/apex-scaffold/apex-common/src/main/java/com/apex/scaffold/common/exception/BaseException.java)                                                                                                   |
| 生成器模板           | `.ftl` 已用 `@Data`                                                                                              | dtoRequest.ftl / dtoResponse.ftl / po.ftl 等                                                                                                                                                                                                                                                                                                          |

### 2.3 当前问题

约 **106 个文件**手写了 getter(、61 个文件手写了 setter)。其中无任何 Lombok 注解、getter/setter 为**纯样板**的生产类约 **40-45 个**,是最符合"可以使用 lombok 完成"语义的改造对象;其余为已有注解但保留少量手写(逻辑 getter/链式 setter/静态工厂/枚举/getter setter 别名),属正确用法,不在本次范围。

## 三、改造范围界定

### 3.1 范围内(改为 Lombok 注解)

**判定标准(需同时满足)**:

1. `src/main` 生产代码;
2. 类上无任何 Lombok 注解(`@Getter/@Setter/@Data/@Builder/@RequiredArgsConstructor/@ToString/@EqualsAndHashCode` 等);
3. 手写 getter/setter 全部为纯样板(单行返回/赋值,无 null 检查、防御性拷贝、计算逻辑、校验或别名映射);
4. 无手写逻辑 getter/setter、无链式方法、无手写 toString/equals/hashCode 的业务语义(有则保留);
5. 非枚举、非接口、非抽象基类(基类字段由子类继承时,若基类自身也是纯样板 POJO 可单独处理,见 App A 逐类复核)。

**已抽查确认的代表类**(实际清单以实施时逐文件复核为准):

- apex-domain: [AuditLogRecord](file:///Users/work/apex-scaffold/apex-domain/src/main/java/com/apex/scaffold/domain/model/record/AuditLogRecord.java)(14 字段纯样板)等 record/model 层纯 POJO。

- apex-application: [DeptDTO](file:///Users/work/apex-scaffold/apex-application/src/main/java/com/apex/scaffold/application/dto/admin/DeptDTO.java)、UserExportDTO、AuditLogDTO、OnlineUserDTO、AccountDeletionRequest 等 DTO。

- apex-infrastructure: [DeptPO](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/DeptPO.java)、SlowSqlProperties、MailConfigurationProperties 等。

- apex-interfaces: [FileUploadRequestDoc](file:///Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/dto/FileUploadRequestDoc.java)、[GenericFileUploadRequestDoc](file:///Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/dto/GenericFileUploadRequestDoc.java)。

- apex-license-spring-boot-starter / apex-example: 满足判定标准的类。

### 3.2 范围外(保持手写不动)

- `src/test` 全部测试代码(无运行期风险,但改动量大、收益低,非必要不动)。

- 已有 Lombok 注解的类(即使带手写 setter,如 TenantEntity 的"仅供持久层使用"setter)。

- 含逻辑 getter/setter 的类:[PhoneNumber.getMasked()/getFormatted()/getWithCountryCode()](file:///Users/work/apex-scaffold/apex-domain/src/main/java/com/apex/scaffold/domain/model/valueobject/PhoneNumber.java#L146-L172)、PageRequest.getOffset() 等。

- 链式 setter:[Result.setTraceId()](file:///Users/work/apex-scaffold/apex-common/src/main/java/com/apex/scaffold/common/result/Result.java)(源码注释明确因与 Lombok void setter 冲突而手写)。

- 复杂构造器/静态工厂:[BaseException](file:///Users/work/apex-scaffold/apex-common/src/main/java/com/apex/scaffold/common/exception/BaseException.java)、[CronExpression](file:///Users/work/apex-scaffold/apex-application/src/main/java/com/apex/scaffold/application/job/CronExpression.java)(不可变、仅一个 getter)、LocalCacheService(构造器注入+业务逻辑)。

- 枚举类:ResultCode、LicenseVersion、ActivityStatus、EducationLevel、TenantEntity.TenantStatus、BaseEntity.LogicalDeletion 等(枚举内嵌类 getter 保持手写)。

- 已有 compat 别名方法的类:[ReportTicketPO.getEvidenceJson()/setEvidenceJson()](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/ReportTicketPO.java#L76-L87)。

- 所有 `serialVersionUID` / `@Serial` 字段:保留手写,不删除(改动会破坏序列化兼容)。

- 所有静态工厂 `of/ofNullable/create/from/success/error/builder…`:保留手写。

- apex-generator 的 `.ftl` 模板:不动(已用 `@Data`)。

## 四、注解策略(已确认)

- **统一** **`@Data`**(含 getter/setter/toString/equals/hashCode),与项目 117 个既有 `@Data` 类、生成器模板风格一致。

- 手写 no-args 构造器保留(项目 DTO/PO 普遍显式声明 `Xxx() {}`,`@Data` 会生成无参构造器,但显式声明可保留 Javadoc 与习惯,不删除);如类无显式构造器则不加。

- 若类继承基类(PO extends BasePO 等),追加 `@EqualsAndHashCode(callSuper = true)`、`@ToString(callSuper = true)`,与 [ReportTicketPO](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/ReportTicketPO.java) 既有写法一致。

- 逻辑方法/兼容方法在 `@Data` 下保留(Lombok 遇同名方法跳过生成),参考 [BasePO](file:///Users/work/apex-scaffold/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/BasePO.java)。

- 字段上的 `@Schema`、`@TableField`、`@TableId`、`@TableName`、`@Version`、`@TableLogic`、jakarta validation 注解等全部原样保留在字段上。

## 五、实施步骤

### 步骤 1 — 生成精确待改清单

对 8 个 `src/main` 源码目录执行逐文件复核(读文件 + grep 交叉验证),按 3.1 判定标准产出最终清单并写入计划文档附录。**不依赖搜索代理的近似统计,实施时以逐文件确认为准**。

甄别口径:

- 手写 getter/setter 全部纯样板 → 改 `@Data`;

- 有任何一行逻辑(判空/计算/别名/返回 this)→ 整类排除或仅保留该手写方法;

- 枚举、接口、抽象类、已有 Lombok 注解 → 排除。

### 步骤 2 — 逐个文件改造

对清单中每个文件:

1. 类声明上方加 `@Data`(继承场景加 `@EqualsAndHashCode(callSuper = true)`、`@ToString(callSuper = true)`),import 相应 `lombok.*`;
2. 删除全部纯样板 getter/setter 方法(连带其 Javadoc);
3. 保留:显式 no-args 构造器、serialVersionUID、静态工厂、逻辑方法、compat 方法、字段注解;
4. 无需手工调整 import 顺序 —— 由 spotless `removeUnusedImports`+palantir 格式兜底,但顺手保证 `lombok.*` import 按字母序加入。

### 步骤 3 — 格式与静态检查

执行:

```bash
source ./scripts/build/init-build-env.sh   # 若仓库存在该脚本;否则跳过
mvnd spotless:apply
```

确认无多余空白、import 顺序正确。**不改动 pom/配置/模板/测试**。

### 步骤 4 — 编译 + 单测 + 覆盖率

```bash
mvnd test          # 全量单测(含 jacoco 覆盖率门槛、spotless check、PMD 默认跳过)
```

若单测阶段资源消耗过大,至少执行:

```bash
mvnd -pl apex-domain,apex-common,apex-infrastructure,apex-application,apex-interfaces -am test
```

### 步骤 5 — 全量 verify 验证

```bash
mvnd verify        # 含 jacoco 覆盖率门槛 + failsafe 集成测试 + PMD(P3C)
```

需确认:

- 编译零错误、零警告(尤其 Lombok 生成的 `equals/hashCode` 与手写 `@Data` 类相性);

- spotless check 通过;

- 覆盖率门槛不回归(Lombok 生成的 toString/equals/hashCode 不计入覆盖率统计需确认 jacoco 无新增未覆盖缺口;既有 jacoco exclude 规则已覆盖 `dto/**`、`config/**` 等,预计无影响);

- 集成测试不回归。

### 步骤 6 — 交付检查

- `git diff --check` 无空白错误;

- `git status --short` 确认仅改动目标 Java 文件;

- 汇总:改了什么(文件数)、为什么、验证了什么、未验证什么。

## 六、风险与规避

| 风险                                            | 影响                                                    | 规避措施                                                                             |
| --------------------------------------------- | ----------------------------------------------------- | -------------------------------------------------------------------------------- |
| Lombok 生成的 `equals/hashCode` 改变序列化/缓存/Map 键语义 | 低(HashMap 键、Redis 序列化均走 getter/字段,`equals` 变化仅影响内存比较) | 保留 serialVersionUID;不做 Map/Set 键依赖等于 hashCode 的假设;先跑全量测试                         |
| `@Data` 生成的 setter 与手写逻辑方法重名                  | 低                                                     | 逻辑方法(如 `getOffset`、`getMasked`)不叫 `getXxx` 纯字段名时无冲突;同名时 Lombok 自动跳过,参考 BasePO 先例 |
| 已经用 `@Data` 的类被误改成"重复注解"                      | 低                                                     | 步骤 1 排除已有 Lombok 注解的类                                                            |
| 继承类 `toString/equals` 不含父类字段                  | 中                                                     | 统一加 `callSuper = true`(与 ReportTicketPO 一致)                                      |
| jacoco 覆盖率门槛因新增 `equals/hashCode` 未覆盖而失败      | 低                                                     | 既有 exclude 已覆盖 dto/config/po;如个别类被门槛约束,评估补测或保持该类手写                               |
| 枚举/Lombok 3.0 兼容/IDE 未装插件                     | 低(纯编译期注解,构建产物无 lombok 运行时依赖)                          | 保持枚举手写;构建走 mvnd,无需 IDE 参与                                                        |
| 误改含逻辑 getter 的类                               | 中                                                     | 步骤 1 逐文件复核,逻辑方法一律保留                                                              |

## 七、假设与决策记录

1. **范围**:仅 `src/main` 纯样板无注解类,约 40-45 个;测试、生成器模板、枚举、含逻辑/别名方法类不动(用户已授权按项目实际情况定夺,此为最优平衡)。
2. **注解策略**:统一 `@Data`;继承类加 `callSuper = true`;显式 no-args 构造器保留。
3. **验证强度**:全量 `mvnd test` + `mvnd verify`。
4. **工具链**:仅使用 `mvnd`(仓库 bias-service 惯例适用与否待实施时以仓库实际脚本为准;先检查 `scripts/build/init-build-env.sh` 是否存在)。
5. **模板**:apex-generator `.ftl` 不动(已用 `@Data`)。
6. 不改 pom、不新增依赖、不新增文件(仅计划文档/实施产物)。

## 八、验证步骤(验收标准)

1. `mvnd test` 全量单测绿(含 jacoco 门槛)。
2. `mvnd verify` 集成测试 + PMD 通过(PMD 默认 skip,如 CI 强制则同检)。
3. `mvnd spotless:apply` 后无差异(格式干净)。
4. `git diff --check` 通过;`git status --short` 仅含目标 Java 文件。
5. 抽查改造前后行为等价性:对每个改造类确认编译产物方法签名一致(setter 返回 void、boolean 字段 `isXxx()` 语义一致)。
6. 报告中说明未运行项(如全仓 verify 若耗时过长可分层说明)。


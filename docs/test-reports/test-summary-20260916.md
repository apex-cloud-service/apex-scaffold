# Apex Scaffold 全模块测试报告

- 执行日期：2026-09-16
- 执行命令：`mvnd clean test -P basic`
- Java：17.0.12-oracle
- Maven Daemon：2.0.0-rc-3
- 总体结果：`BUILD SUCCESS`

## 模块结果

| 模块 | 测试数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| apex-common | 497 | 0 | 0 | 0 |
| apex-license-spring-boot-starter | 19 | 0 | 0 | 0 |
| apex-license-tools | 0 | 0 | 0 | 0 |
| apex-domain | 398 | 0 | 0 | 0 |
| apex-infrastructure | 545 | 0 | 0 | 0 |
| apex-application | 510 | 0 | 0 | 0 |
| apex-interfaces | 440 | 0 | 0 | 3 |
| apex-example | 0 | 0 | 0 | 0 |
| apex-generator | 19 | 0 | 0 | 0 |
| **合计** | **2428** | **0** | **0** | **3** |

## 重点验证

- `LayeringArchTest`：6 个测试全部通过。
- `GlobalExceptionHandlerTest`：18 个测试全部通过。
- `apex-application`：510 个测试全部通过，包含恢复并适配后的 Office 测试。
- JaCoCo 覆盖率检查通过。

## 质量检查说明

本次全量测试通过。构建日志仍包含 Lombok 和 deprecated API 警告，但未导致测试失败。根 POM 中 `pmd.skip` 默认为 `true`，因此 PMD 默认不会执行规则扫描。

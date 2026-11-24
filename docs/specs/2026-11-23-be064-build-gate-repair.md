# BE-064 构建门禁修复规格

## 背景

BE-063 Wave E 的全仓 `verify` 被两个独立问题阻断：PMD 6.55 内置 ASM 9.4 无法解析 Java 21 major 65 依赖字节码；`ExampleDataSourceTimeoutTest` 在全仓负载下测得耗时超过固定 12 秒断言。

## 目标

保持 PMD/P3C 规则及失败门禁启用，解决 PMD 分析依赖 classpath 的字节码兼容；让握手超时回归稳定验证实际 socket 超时而不受无关调度抖动误报。

## 影响面（模块+接口+数据）

构建影响根 `pom.xml` 的 Maven PMD 插件依赖解析；测试影响 `apex-example` 的 `ExampleDataSourceTimeoutTest`。不改变运行服务、接口、模块坐标、数据结构或生产配置。

## 兼容性

PMD 规则集与 `p3c-pmd` 版本保持不变，仅采用与 PMD 6 API 二进制兼容且支持 Java 21 class file 的 ASM 补丁版本。超时测试继续对本机 TCP 握手执行真实数据库协议超时验证，不移除断言或质量门禁。

## 回滚方案

按 BE-064 单独回滚 `pom.xml` 与该测试文件的变更；保留 BE-063 Wave E 失败记录，不覆盖历史报告。

## 验收标准

先验证 `apex-auth-service` PMD 全量分析不再产生 major 65 异常，再独立重复运行超时用例；之后在固定 JDK/mvnd 下执行全仓 `mvnd -T4 -B verify`，并在同一次成功运行后重新生成测试报告。不得运行 `clean` 删除其他工作区既有报告。

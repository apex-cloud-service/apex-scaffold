# 限流算法集成测试报告

**报告日期**: 2026-09-12
**项目版本**: 1.2.0

## 验证命令

```bash
source ./scripts/build/init-build-env.sh
mvnd -pl apex-common,apex-infrastructure -am test
mvnd spotless:apply
mvnd spotless:check
```

## 结果

| 项目 | 结果 |
|---|---|
| Java | 17.0.12 |
| 测试模块 | apex-common、apex-domain、apex-infrastructure |
| 测试总数 | 976 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| JaCoCo 检查 | 通过 |
| Spotless | 通过 |
| 构建 | 成功 |

## 覆盖内容

- 令牌桶、漏桶和分片滑动窗口基础行为；
- LocalRateLimiter 算法策略兼容性；
- Redis 降级限流相关代码编译与模块回归；
- MQ 消费、缓存服务、自适应限流相关基础设施模块回归；
- 租户隔离 ArchUnit 检查。

## 说明

全局 `git diff --check` 仍会报告仓库历史文档中的尾随空格。本次新增和修改的 Java 文件已通过 Spotless；未批量修改无关历史文档。

配置模板位于 `apex-interfaces/src/main/resources/application-rate-limit.yml`，公共运行配置中的 `apex.ratelimit` 区块位于 `application.yml`，因此 Spring Boot 启动时会读取公共配置。

## 后续风险

MQ 漏桶当前采用快速拒绝并依赖消息消费框架重试；多实例场景仍应优先使用 Redis 分布式限流。本报告覆盖现有自动化回归测试，未替代依赖真实 MySQL、Redis、RocketMQ 的环境集成测试。

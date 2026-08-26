# Apex Scaffold 项目记忆

## 项目定位
面向中小企业的 Java 17 / Spring Boot 3.5 / WebFlux 响应式 / DDD 五层架构的后端开发脚手架，计划三档售卖（基础版 MIT / 标准版 / 企业版）。

## 技术底座（不可变）
- Java 17 + Spring Boot 3.5 + WebFlux + MyBatis-Plus + MySQL + Redis + RocketMQ
- Maven 多模块：apex-common → domain → infrastructure → application → interfaces + apex-example
- 编译环境：JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home，Maven 3.9.8（~/.sdkman），offline 模式 `mvn -o`

## 关键约定
- **全库禁止出现 bias/Bias/BIAS 字样**（硬性门禁，grep 零命中），统一使用 apex.* 命名空间
- 缓存前缀 `apex:`、指标名 `apex.api.*`、配置前缀 `apex.*`
- 数据库迁移：单一干净基线 `V1__init_schema.sql`（27 张通用表），数据库名 apex_data，由 JDBC URL 提供不写入迁移
- dev 用 H2 内存库（MODE=MySQL），prod 用 MySQL + Druid；测试 schema 为 `schema-it.sql`（H2 兼容 27 表）
- 业务代码零残留：interest/battle/elo/face/resonance/radar/eyesight/future-partner/personal-bias/aesthetic/recommendation 域全部移除
- 通用 Handler 集合：Auth/User/File/Health/Base/Authorization/AdminUserManagement/AbstractStorageProxy

## 已完成重构（2026-08-26）
- 阶段 A（工程基线）+ 阶段 B（去业务化）完成，全库 bias 零命中，1786 个测试全绿，git 基线已建立
- 详见 .workbuddy/memory/2026-08-26.md

## 待办路线图
- 阶段 C：系统管理能力（菜单/字典/参数）、管理后台完善、example 增强
- 阶段 D：Profile 实质化、License 强制启用、双轨许可证
- 阶段 E：文档重构、营销物料、v1.1.0 发布

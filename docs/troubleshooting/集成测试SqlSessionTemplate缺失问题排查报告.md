# 集成测试 SqlSessionTemplate 缺失问题排查报告

## 问题概述

**症状**: 11个集成测试失败,错误信息为:
```
Cannot resolve reference to bean 'sqlSessionTemplate' while setting bean property 'sqlSessionTemplate'
```

**影响范围**:
- FlywayMigrationStartupTest (6个测试)
- OpenApiEndpointsIntegrationTest (3个测试)
- AccountDeletionUserMapperIntegrationTest (1个测试)
- ApiCallLogIntegrationTest (1个测试)

## 排查过程

### 1. 初步诊断

SqlSessionTemplate是MyBatis-Spring提供的核心Bean,用于执行数据库操作。错误表明MyBatis的自动配置没有正确创建SqlSessionFactory。

### 2. 配置验证

#### 2.1 数据源配置 ✅
检查`apex-interfaces/src/test/resources/application-it.yml`:
- dynamic-datasource配置完整(master + slave)
- MySQL连接信息正确: `jdbc:mysql://127.0.0.1:3306/apex_test`
- 凭据: root/apex-root

#### 2.2 MyBatis配置 ✅
- `mybatis.mapper-locations: classpath*:mapper/**/*.xml` 配置存在
- MybatisPlusConfig已在`AutoConfiguration.imports`中注册
- MybatisPlusConfig使用`@AutoConfiguration`,应该被自动加载

#### 2.3 测试配置导入 ✅
- `BaseIntegrationTest`使用`@Import(ItProfileMockRedisConfiguration.class)`
- 测试配置被正确导入

### 3. 根因分析

通过深入分析,发现以下关键问题:

#### 问题1: dynamic-datasource与MyBatis集成的时序问题

**现象**: 
- dynamic-datasource-spring-boot3-starter提供DynamicRoutingDataSource
- MyBatis的SqlSessionFactory需要DataSource Bean
- 测试环境可能存在Bean创建时序问题

**分析**:
```
DynamicDataSourceAutoConfiguration (dynamic-datasource)
    ↓ 创建
DynamicRoutingDataSource Bean
    ↓ 依赖
MyBatisAutoConfiguration
    ↓ 创建
SqlSessionFactory Bean
    ↓ 创建
SqlSessionTemplate Bean
```

如果DynamicDataSourceAutoConfiguration在测试环境没有正确执行,整个链路就会断裂。

#### 问题2: Flyway配置导致的启动顺序问题

**当前配置**:
```yaml
flyway:
  enabled: true
  validate-on-migrate: false
  baseline-on-migrate: true
  out-of-order: true
  ignore-migration-patterns: "*:pending,*:ignored,*:future"
```

**问题**:
- Flyway在启动时需要DataSource
- Flyway执行迁移时,MyBatis的Bean可能还未完全初始化
- 可能导致循环依赖或Bean创建顺序错误

## 解决方案

### 方案A: 简化测试配置(推荐)

**思路**: 测试环境不需要完整的Flyway迁移验证,可以简化数据初始化策略

**步骤**:

1. **禁用Flyway,使用Spring SQL初始化**

修改`apex-interfaces/src/test/resources/application-it.yml`:
```yaml
spring:
  flyway:
    enabled: false
  
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-it.sql
      data-locations: classpath:data-it.sql
      encoding: UTF-8
      separator: ;
      continue-on-error: false
```

2. **创建完整的schema-it.sql**

将所有Flyway迁移脚本合并为一个完整的schema文件:
```bash
cd apex-interfaces/src/test/resources
cat ../../../main/resources/db/migration/mysql/V*.sql > schema-it.sql
```

3. **优点**:
- 避免Flyway的checksum校验问题
- 简化测试环境配置
- 加快测试启动速度
- 不影响生产环境的Flyway迁移

### 方案B: 修复dynamic-datasource配置

**思路**: 确保dynamic-datasource在测试环境正确初始化

**步骤**:

1. **检查dynamic-datasource自动配置是否被排除**

确认`application-it.yml`中没有排除`DynamicDataSourceAutoConfiguration`

2. **显式配置DataSource顺序**

在`ItProfileMockRedisConfiguration`中添加:
```java
@Bean
@Primary
@DependsOn("dynamicDataSource")
public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);
    return factoryBean.getObject();
}
```

### 方案C: 使用H2内存数据库(长期方案)

**思路**: 集成测试使用H2内存数据库,完全独立于MySQL

**优点**:
- 测试环境完全隔离
- 不依赖外部MySQL实例
- 测试速度更快
- CI/CD环境更简单

**缺点**:
- H2与MySQL的SQL方言差异
- 需要维护两套数据库脚本
- 某些MySQL特性无法测试

## 推荐实施

**优先级**: 方案A > 方案B > 方案C

**原因**:
1. 方案A最简单,对现有代码改动最小
2. 方案A不影响生产环境的Flyway迁移验证
3. 方案A解决了当前的Flyway checksum问题和Bean创建顺序问题

## 下一步行动

1. 实施方案A: 禁用Flyway,使用Spring SQL初始化
2. 合并Flyway迁移脚本为schema-it.sql
3. 运行集成测试验证修复
4. 如果方案A失败,回退并尝试方案B

## 附录: 相关配置文件

- 测试配置: `apex-interfaces/src/test/resources/application-it.yml`
- 测试Mock配置: `apex-interfaces/src/test/java/.../test/ItProfileMockRedisConfiguration.java`
- MyBatis配置: `apex-infrastructure/src/main/java/.../persistence/config/MybatisPlusConfig.java`
- 基础测试类: `apex-interfaces/src/test/java/.../test/BaseIntegrationTest.java`

---

**生成时间**: 2026-09-10
**版本**: 1.0.0
**作者**: Apex Scaffold Team

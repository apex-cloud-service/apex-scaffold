# ============================================================
# 数据库部署配置（由前端配置页面采集后生成）
# ============================================================
# 数据库类型: ${dbType}
# 说明: 本片段可合并至 application-{profile}.yml。
#   密码统一通过环境变量 ${dbMasterPasswordPlaceholder} 注入，
#   不允许硬编码明文密码。

spring:
  datasource:
    dynamic:
      primary: master
      strict: true
      datasource:
        master:
          url: ${jdbcUrl}
          username: ${username}
          password: ${dbMasterPasswordPlaceholder}
          driver-class-name: ${driverClassName}
          type: com.alibaba.druid.pool.DruidDataSource
          druid:
            db-type: ${dbType}
            initial-size: 5
            min-idle: 5
            max-active: ${dbMaxActivePlaceholder}
            max-wait: 3000
            validation-query: ${validationQuery}
            test-while-idle: true
            pool-prepared-statements: true
            max-pool-prepared-statement-per-connection-size: 20
            filters: stat
  flyway:
    enabled: true
    locations: classpath:db/migration/${dbType}
    validate-on-migrate: true
    baseline-on-migrate: false

# MyBatis-Plus 分页方言（与数据库类型保持一致）
mybatis-plus:
  configuration:
    db-type: ${dbType}

#!/bin/bash
###############################################################################
# scaffold-phase5-6.sh - 微服务化改造 Phase 5/6 非破坏性模块骨架生成器
#
# 【用途】
#   为后续分阶段执行"办公服务抽取(Phase 5) / 系统服务收敛(Phase 6)"生成模块骨架：
#   - Phase 5：apex-office（库）+ apex-office-service（服务，端口 8084）
#   - Phase 6：apex-system-service（服务，端口 8082）
#   生成内容包括：模块 pom.xml、bootstrap.yml / application.yml、启动类占位、
#   DDD 分层目录占位、独立库 Flyway V1 目录占位。
#
# 【非破坏性保证】（详见设计文档 §7）
#   1. 默认 dry-run：仅打印将创建的文件路径，不写盘。
#   2. --apply 才实际创建；已存在文件一律跳过（幂等）。
#   3. 绝不修改根 pom.xml：生成后须由人工确认后再手动登记 <modules>，避免破坏 reactor。
#   4. 遵循 scripts/lib/common.sh 规范（log_* / get_project_root / 错误陷阱）。
#
# 【用法】
#   ./scripts/microservices/scaffold-phase5-6.sh --phase 5            # 预览
#   ./scripts/microservices/scaffold-phase5-6.sh --phase 6 --apply     # 实际生成
#
# @author Apex Team
# 日期：2026-10-10
# 版本：v1.0.0
###############################################################################

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

# shellcheck disable=SC2034
PROJECT_ROOT=$(get_project_root)
APPLY=0
PHASE=""

# 版本权威来源（与根 pom 当前版本一致；后续改用 CI 占位符时人工核对）
readonly APEX_VERSION="1.3.1"
readonly GROUP_ID="com.apex.scaffold"

###############################################################################
# 使用说明
###############################################################################
usage() {
    cat <<'EOF'
用法: scaffold-phase5-6.sh --phase 5|6 [--apply]

参数:
  --phase 5|6   要生成骨架的阶段（必填）
                5 = 办公服务抽取: apex-office(库) + apex-office-service(8084)
                6 = 系统服务收敛: apex-system-service(8082)
  --apply       实际创建文件；缺省为 dry-run（仅预览，不写盘）
  -h, --help    显示帮助

非破坏性约定:
  1. 默认 dry-run；--apply 才写盘。
  2. 已存在文件跳过（幂等）。
  3. 绝不修改根 pom.xml —— 生成后须人工确认再手动登记 <modules>。
EOF
}

###############################################################################
# 参数解析
###############################################################################
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --phase)
                shift
                if [[ -z "${1:-}" ]]; then
                    log_error "--phase 需要一个参数 (5 或 6)"
                    usage
                    return 2
                fi
                PHASE="$1"
                ;;
            --apply)
                APPLY=1
                ;;
            -h|--help)
                usage
                return 1
                ;;
            *)
                log_error "未知参数: $1"
                usage
                return 2
                ;;
        esac
        shift
    done

    if [[ "$PHASE" != "5" && "$PHASE" != "6" ]]; then
        log_error "--phase 必须为 5 或 6（当前: ${PHASE:-<空>}）"
        usage
        return 2
    fi
    return 0
}

###############################################################################
# 写文件（dry-run 打印；--apply 才写，且幂等跳过）
###############################################################################
write_file() {
    local file="$1"
    local content="$2"

    if [[ $APPLY == 0 ]]; then
        log_info "[dry-run] 将创建: ${file}"
        return 0
    fi

    if [[ -f "$file" ]]; then
        log_warn "[跳过] 已存在: ${file}"
        return 0
    fi

    mkdir -p "$(dirname "$file")"
    printf '%s' "$content" > "$file"
    log_success "已创建: ${file}"
}

###############################################################################
# Phase 5 - 办公服务骨架（apex-office 库 + apex-office-service 服务）
###############################################################################
generate_phase5() {
    local lib_pom="$PROJECT_ROOT/apex-office/pom.xml"
    local svc_pom="$PROJECT_ROOT/apex-office-service/pom.xml"
    local svc_app="$PROJECT_ROOT/apex-office-service/src/main/java/com/apex/scaffold/officeservice/OfficeServiceApplication.java"
    local bootstrap="$PROJECT_ROOT/apex-office-service/src/main/resources/bootstrap.yml"
    local application="$PROJECT_ROOT/apex-office-service/src/main/resources/application.yml"
    local application_basic="$PROJECT_ROOT/apex-office-service/src/main/resources/application-basic.yml"

    local lib_pom_content
    lib_pom_content=$(cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!--
  - Apex Scaffold - 办公模块 POM 配置文件
  - 微服务化改造 Phase 5：将办公能力（组织/审批/日历/消息/三平台同步）从单体抽取为独立模块，
  - 供 apex-office-service 复用；本模块聚合办公领域模型、仓储契约与应用服务。
  - 约束：本模块 <parent> 指向根 POM（库模块），业务代码禁止自行声明第三方版本号。
  - 注意：本骨架由 scaffold-phase5-6.sh 生成，业务代码待 Phase 5 分阶段迁移。
  - @author Apex Team
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>${GROUP_ID}</groupId>
        <artifactId>apex-scaffold</artifactId>
        <version>${APEX_VERSION}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>apex-office</artifactId>
    <packaging>jar</packaging>
    <name>apex-office</name>
    <description>Apex Scaffold - Office Module (组织/审批/日历/消息/三平台同步 领域+应用)</description>

    <dependencies>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
EOF
)

    local svc_pom_content
    svc_pom_content=$(cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!--
  - Apex Scaffold - 办公服务模块 POM
  - 微服务化改造 Phase 5：将办公能力独立为可单独启动/发布/扩展的服务。
  - 功能：办公 Router/Handler/DTO（interfaces 层）+ 办公应用服务（引用 apex-office）
  -       + 独立 apex_office 库 Flyway 迁移 + 三平台(企微/钉钉/飞书)组织与审批同步。
  - 约束：本模块 <parent> 指向 apex-services-bom；业务代码禁止自行声明第三方版本号（版本单源）。
  - 注意：本骨架由 scaffold-phase5-6.sh 生成，业务代码待 Phase 5 分阶段迁移。
  - @author Apex Team
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>${GROUP_ID}</groupId>
        <artifactId>apex-services-bom</artifactId>
        <version>${APEX_VERSION}</version>
        <relativePath>../apex-services-bom/pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>apex-office-service</artifactId>
    <packaging>jar</packaging>
    <name>apex-office-service</name>
    <description>Apex Scaffold - 办公服务 (端口 8084 / 独立 apex_office 库 / 三平台同步)</description>

    <dependencies>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-common</artifactId>
        </dependency>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-office</artifactId>
        </dependency>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-api-contract</artifactId>
        </dependency>

        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>\${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
            <version>\${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webflux-api</artifactId>
            <version>\${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
            <version>\${flyway.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
            <version>\${flyway.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
            <version>\${flyway.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>\${mysql-connector.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>\${postgresql.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF
)

    local svc_app_content
    svc_app_content=$(cat <<'EOF'
package com.apex.scaffold.officeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 办公服务启动类（Phase 5 骨架占位）。
 *
 * <p>微服务化改造 Phase 5：将办公能力（组织 / 审批 / 日历 / 消息 / 三平台同步）
 * 从单体抽取为独立可启动/发布/扩展的服务 {@code apex-office-service}（端口 8084）。
 *
 * <h2>扫描范围</h2>
 * <ul>
 *   <li>{@code com.apex.scaffold.officeservice} - 本服务接口层/配置/安全</li>
 *   <li>{@code com.apex.scaffold.office} - 办公领域/应用模块（apex-office）</li>
 *   <li>{@code com.apex.scaffold.common} - 共享基础组件</li>
 * </ul>
 *
 * <p><b>注意</b>：Phase 5 迁移完成前，办公应用层尚依赖单体系统类，需先经
 * apex-api-contract 内部契约桥接后方可实际编译运行。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication(
        scanBasePackages = {"com.apex.scaffold.officeservice", "com.apex.scaffold.office", "com.apex.scaffold.common"})
@EnableDiscoveryClient
@EnableScheduling
public class OfficeServiceApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OfficeServiceApplication.class, args);
    }
}
EOF
)

    local bootstrap_content
    bootstrap_content=$(cat <<'EOF'
# ============================================================
# Apex Scaffold - 办公服务 Bootstrap 配置（Phase 5 骨架占位）
# ============================================================
# 【作用】
#   在 Spring Boot 启动早期加载，用于连接 Nacos 配置中心与服务发现，
#   使办公服务作为独立微服务注册到 Nacos，供网关 lb://apex-office-service 路由。
# 【加载顺序】 bootstrap.yml -> Nacos 远端配置 -> application.yml -> application-{profile}.yml
# 【安全提示】 生产环境通过 NACOS_SERVER_ADDR 注入内网地址，禁止固化公网地址。
# @author Apex Team
# ============================================================

spring:
  application:
    name: apex-office-service
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:public}
        group: DEFAULT_GROUP
        file-extension: yml
        refresh-enabled: true
        enabled: ${NACOS_CONFIG_ENABLED:false}
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: DEFAULT_GROUP
        enabled: ${NACOS_DISCOVERY_ENABLED:false}
        register-enabled: ${NACOS_REGISTER_ENABLED:false}
EOF
)

    local application_content
    application_content=$(cat <<'EOF'
# ============================================================
# Apex Scaffold - 办公服务 application.yml（Phase 5 骨架占位）
# ============================================================
# 【独立库】 办公服务使用独立数据库 apex_office（设计文档 §4.3），
#   表结构：off_department / off_employee / off_binding / off_message /
#          off_message_recipient / off_message_template / off_sync_history /
#          off_calendar / off_approval_process。
# @author Apex Team
# ============================================================

server:
  port: 8084

spring:
  application:
    name: apex-office-service

  codec:
    max-in-memory-size: 256MB

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/apex_office?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: ${DB_PASSWORD:root}

  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration/mysql
    out-of-order: true

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

logging:
  level:
    root: INFO
    com.apex.scaffold.office: DEBUG
    com.apex.scaffold.officeservice: DEBUG
    org.springframework.web: DEBUG

springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
  default-consumes-media-type: application/json
  default-produces-media-type: application/json
EOF
)

    # 库模块分层目录占位
    local lib_layers=(
        "apex-office/src/main/java/com/apex/scaffold/office/domain/.gitkeep"
        "apex-office/src/main/java/com/apex/scaffold/office/application/.gitkeep"
        "apex-office/src/main/java/com/apex/scaffold/office/infrastructure/.gitkeep"
    )

    # 服务模块 interfaces 层 + Flyway 双套目录占位
    local svc_layers=(
        "apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/router/.gitkeep"
        "apex-office-service/src/main/resources/db/migration/mysql/.gitkeep"
        "apex-office-service/src/main/resources/db/migration/postgresql/.gitkeep"
    )

    print_header "Phase 5 - 办公服务骨架 (apex-office + apex-office-service / 8084)"

    write_file "$lib_pom" "$lib_pom_content"
    write_file "$svc_pom" "$svc_pom_content"
    write_file "$svc_app" "$svc_app_content"
    write_file "$bootstrap" "$bootstrap_content"
    write_file "$application" "$application_content"
    write_file "$application_basic" "# 办公服务 basic profile（本地/测试）占位，待 Phase 5 补充。\n"

    local f
    for f in "${lib_layers[@]}" "${svc_layers[@]}"; do
        if [[ $APPLY == 0 ]]; then
            log_info "[dry-run] 将创建: ${PROJECT_ROOT}/${f}"
        else
            if [[ -e "$PROJECT_ROOT/$f" ]]; then
                log_warn "[跳过] 已存在: ${PROJECT_ROOT}/${f}"
            else
                mkdir -p "$(dirname "$PROJECT_ROOT/$f")"
                touch "$PROJECT_ROOT/$f"
                log_success "已创建: ${PROJECT_ROOT}/${f}"
            fi
        fi
    done
}

###############################################################################
# Phase 6 - 系统服务骨架（apex-system-service 服务）
###############################################################################
generate_phase6() {
    local svc_pom="$PROJECT_ROOT/apex-system-service/pom.xml"
    local svc_app="$PROJECT_ROOT/apex-system-service/src/main/java/com/apex/scaffold/systemservice/SystemServiceApplication.java"
    local bootstrap="$PROJECT_ROOT/apex-system-service/src/main/resources/bootstrap.yml"
    local application="$PROJECT_ROOT/apex-system-service/src/main/resources/application.yml"
    local application_basic="$PROJECT_ROOT/apex-system-service/src/main/resources/application-basic.yml"

    local svc_pom_content
    svc_pom_content=$(cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!--
  - Apex Scaffold - 系统服务模块 POM
  - 微服务化改造 Phase 6：将系统能力（user/rbac/config/log/job/tenant/sso/oauth2…
  -       + ApiRouter 拆分）收敛为可独立启动/发布/扩展的服务。
  - 约束：本模块 <parent> 指向 apex-services-bom；业务代码禁止自行声明第三方版本号（版本单源）。
  - 注意：本骨架由 scaffold-phase5-6.sh 生成，业务代码待 Phase 6 分阶段迁移。
  - @author Apex Team
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>${GROUP_ID}</groupId>
        <artifactId>apex-services-bom</artifactId>
        <version>${APEX_VERSION}</version>
        <relativePath>../apex-services-bom/pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>apex-system-service</artifactId>
    <packaging>jar</packaging>
    <name>apex-system-service</name>
    <description>Apex Scaffold - 系统服务 (端口 8082 / 独立 apex_system 库 / ApiRouter 拆分收敛)</description>

    <dependencies>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-common</artifactId>
        </dependency>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-infrastructure</artifactId>
        </dependency>
        <dependency>
            <groupId>${GROUP_ID}</groupId>
            <artifactId>apex-api-contract</artifactId>
        </dependency>

        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>\${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
            <version>\${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webflux-api</artifactId>
            <version>\${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
            <version>\${flyway.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
            <version>\${flyway.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
            <version>\${flyway.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>\${mysql-connector.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>\${postgresql.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF
)

    local svc_app_content
    svc_app_content=$(cat <<'EOF'
package com.apex.scaffold.systemservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 系统服务启动类（Phase 6 骨架占位）。
 *
 * <p>微服务化改造 Phase 6：将系统能力（user/rbac/config/log/job/tenant/sso/oauth2）
 * 与 {@code ApiRouter}（约 2582 行）拆分收敛为独立可启动/发布/扩展的服务
 * {@code apex-system-service}（端口 8082）。
 *
 * <h2>扫描范围</h2>
 * <ul>
 *   <li>{@code com.apex.scaffold.systemservice} - 本服务接口层/配置/安全</li>
 *   <li>{@code com.apex.scaffold.system} - 系统领域/应用/基础设施模块</li>
 *   <li>{@code com.apex.scaffold.common} - 共享基础组件</li>
 * </ul>
 *
 * <p><b>注意</b>：Phase 6 迁移完成前，请勿将本模块登记入根 pom 参与 reactor 构建。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication(
        scanBasePackages = {"com.apex.scaffold.systemservice", "com.apex.scaffold.system", "com.apex.scaffold.common"})
@EnableDiscoveryClient
@EnableScheduling
public class SystemServiceApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SystemServiceApplication.class, args);
    }
}
EOF
)

    local bootstrap_content
    bootstrap_content=$(cat <<'EOF'
# ============================================================
# Apex Scaffold - 系统服务 Bootstrap 配置（Phase 6 骨架占位）
# ============================================================
# 【作用】
#   在 Spring Boot 启动早期加载，用于连接 Nacos 配置中心与服务发现，
#   使系统服务作为独立微服务注册到 Nacos，供网关 lb://apex-system-service 路由。
# 【加载顺序】 bootstrap.yml -> Nacos 远端配置 -> application.yml -> application-{profile}.yml
# 【安全提示】 生产环境通过 NACOS_SERVER_ADDR 注入内网地址，禁止固化公网地址。
# @author Apex Team
# ============================================================

spring:
  application:
    name: apex-system-service
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:public}
        group: DEFAULT_GROUP
        file-extension: yml
        refresh-enabled: true
        enabled: ${NACOS_CONFIG_ENABLED:false}
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: DEFAULT_GROUP
        enabled: ${NACOS_DISCOVERY_ENABLED:false}
        register-enabled: ${NACOS_REGISTER_ENABLED:false}
EOF
)

    local application_content
    application_content=$(cat <<'EOF'
# ============================================================
# Apex Scaffold - 系统服务 application.yml（Phase 6 骨架占位）
# ============================================================
# 【独立库】 系统服务使用独立数据库 apex_system（由旧库 apex_data 更名而来），
#   承载 user/rbac/config/log/job/tenant/sso/oauth2 等系统数据。
# @author Apex Team
# ============================================================

server:
  port: 8082

spring:
  application:
    name: apex-system-service

  codec:
    max-in-memory-size: 256MB

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/apex_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: ${DB_PASSWORD:root}

  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration/mysql
    out-of-order: true

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

logging:
  level:
    root: INFO
    com.apex.scaffold.system: DEBUG
    com.apex.scaffold.systemservice: DEBUG
    org.springframework.web: DEBUG

springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
  default-consumes-media-type: application/json
  default-produces-media-type: application/json
EOF
)

    # 服务模块 interfaces 层 + Flyway 双套目录占位
    local svc_layers=(
        "apex-system-service/src/main/java/com/apex/scaffold/systemservice/interfaces/router/.gitkeep"
        "apex-system-service/src/main/resources/db/migration/mysql/.gitkeep"
        "apex-system-service/src/main/resources/db/migration/postgresql/.gitkeep"
    )

    print_header "Phase 6 - 系统服务骨架 (apex-system-service / 8082)"

    write_file "$svc_pom" "$svc_pom_content"
    write_file "$svc_app" "$svc_app_content"
    write_file "$bootstrap" "$bootstrap_content"
    write_file "$application" "$application_content"
    write_file "$application_basic" "# 系统服务 basic profile（本地/测试）占位，待 Phase 6 补充。\n"

    local f
    for f in "${svc_layers[@]}"; do
        if [[ $APPLY == 0 ]]; then
            log_info "[dry-run] 将创建: ${PROJECT_ROOT}/${f}"
        else
            if [[ -e "$PROJECT_ROOT/$f" ]]; then
                log_warn "[跳过] 已存在: ${PROJECT_ROOT}/${f}"
            else
                mkdir -p "$(dirname "$PROJECT_ROOT/$f")"
                touch "$PROJECT_ROOT/$f"
                log_success "已创建: ${PROJECT_ROOT}/${f}"
            fi
        fi
    done
}

###############################################################################
# 主流程
###############################################################################
main() {
    parse_args "$@" || return $?

    if [[ $APPLY == 0 ]]; then
        log_warn "当前为 dry-run 模式（仅预览），使用 --apply 实际创建。"
    fi

    if [[ "$PHASE" == "5" ]]; then
        generate_phase5
    else
        generate_phase6
    fi

    echo ""
    log_warn "提示：本脚本绝不修改根 pom.xml。生成后请人工核对骨架，再手动将新模块加入根 pom.xml 的 <modules>。"
    log_warn "提示：Phase 5 还必须在 apex-api-contract 建立用户读取内部契约并解耦 office 对系统类的直接依赖后才能实际编译。"
}

main "$@"
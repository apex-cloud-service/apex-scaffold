package com.apex.scaffold.bootall;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** JVM 生命周期资源只由父上下文持有，业务 Bean 不在父上下文装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(com.apex.scaffold.common.config.ApexBlockingIoProperties.class)
@Import(com.apex.scaffold.common.scheduler.BlockingIoSchedulers.class)
class BootAllParentConfiguration {}

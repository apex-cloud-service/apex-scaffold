package com.apex.scaffold.bootall;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * 单个业务域的子上下文配置入口。
 *
 * <p>域由 {@code apex.component.<domain>.enabled} 相互排斥地选定；该配置本身不扫描任何
 * 业务包，全部装配来自组件自动配置入口，避免复制业务实现。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class BootAllDomainConfiguration {}

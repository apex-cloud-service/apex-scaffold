package com.apex.scaffold.infrastructure.remote.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 日志配置入口；日志服务由工厂构造时可选注入，不再使用启动后静态回调。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
@Import(ManagedWebClientInitializer.class)
public class ManagedWebClientLogConfig {}

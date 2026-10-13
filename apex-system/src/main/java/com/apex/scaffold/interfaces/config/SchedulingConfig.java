package com.apex.scaffold.interfaces.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务总开关配置类。
 *
 *
 *<p>正常服务默认启用定时任务调度；一次性维护工具可设置 {@code apex.scheduling.enabled=false}，
 *避免独立维护进程与线上实例同时消费任务。该配置确保在执行数据修复或批量维护时，
 *可以临时禁用所有 Spring 定时任务，防止重复消费和数据不一致。</p>
 *
 *<h2>配置项</h2>
 *<ul>
 *  <li>{@code apex.scheduling.enabled} - 定时任务开关，默认 true；设为 false 时所有 @Scheduled 方法不执行</li>
 *</ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "apex.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}

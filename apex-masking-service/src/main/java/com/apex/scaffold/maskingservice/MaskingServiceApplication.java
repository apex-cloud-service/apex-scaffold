package com.apex.scaffold.maskingservice;

import com.apex.scaffold.maskingservice.config.MaskingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 独立脱敏服务入口，仅扫描本服务，避免意外装配业务模块。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication
@EnableConfigurationProperties(MaskingProperties.class)
public class MaskingServiceApplication {
    /** 启动服务。 @param args 启动参数 */
    public static void main(String[] args) {
        SpringApplication.run(MaskingServiceApplication.class, args);
    }
}

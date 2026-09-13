# SPI插件机制使用指南

> **配套架构图**: [SPI插件架构图](../diagrams/43_spi_plugin_architecture.svg)

## 概述

Apex Scaffold 从 v1.2.0 开始支持通过 Java ServiceLoader 机制动态加载存储后端实现。这允许用户在不修改核心代码的情况下,通过添加新的 JAR 包来扩展支持的存储类型(如阿里云 OSS、AWS S3、腾讯云 COS 等)。

## 架构设计

```
┌─────────────────────────────────────┐
│       Application Layer             │
│   (业务层调用 StorageService)        │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│    StorageServiceProvider           │
│   (Spring Bean,管理存储服务实例)     │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│    StorageProvider (SPI Interface)  │
│   (定义存储操作规范)                  │
└──────────────┬──────────────────────┘
               │
       ┌───────┴────────┐
       │                │
       ▼                ▼
┌─────────────┐  ┌──────────────┐
│ MinioImpl   │  │ TosImpl      │
│ (内置)      │  │ (内置)       │
└─────────────┘  └──────────────┘
       │                │
       └────────┬───────┘
                │
                ▼
┌─────────────────────────────────────┐
│  User Custom Implementation         │
│  (通过 SPI 扩展,如 OSS/S3/COS)      │
└─────────────────────────────────────┘
```

## 内置存储提供者

> **注意**: MinIO 与火山引擎 TOS 的内置存储能力由 Spring Bean 实现（`MinioServiceImpl` / `VolcEngineStorageServiceImpl`），
> 通过 `storage.*` 配置激活，无需通过 SPI 注册。
>
> 本仓库**不再内置** SPI 存储实现类。`StorageProvider` 接口作为**第三方扩展点**保留：
> 若需接入阿里云 OSS、AWS S3、腾讯云 COS 等，请按下方步骤自行实现并打包为独立 JAR。

### 内置存储（Spring Bean 方式）

```yaml
# MinIO
storage:
  minio:
    enabled: true
    endpoint: http://localhost:9000
    access-key: ${MINIO_ACCESS_KEY:}
    secret-key: ${MINIO_SECRET_KEY:}
    bucket-name: apex

# 火山引擎 TOS
storage:
  volcengine:
    enabled: true
    access-key: ${TOS_ACCESS_KEY:}
    secret-key: ${TOS_SECRET_KEY:}
    region: cn-beijing
    endpoint: https://tos-cn-beijing.volces.com
    bucket-name: apex-photo
```

## 如何扩展新的存储提供者

### 步骤 1: 创建实现类

创建一个新的类实现 `com.apex.scaffold.common.spi.StorageProvider` 接口:

```java
package com.example.storage;

import com.apex.scaffold.common.spi.StorageProvider;
import java.io.InputStream;
import reactor.core.publisher.Mono;

public class AliOssStorageProvider implements StorageProvider {
    
    @Override
    public String getProviderName() {
        return "oss";
    }
    
    @Override
    public void initialize() {
        // 初始化 OSS 客户端
    }
    
    @Override
    public Mono<String> upload(String bucketName, String objectName, 
                               InputStream inputStream, String contentType) {
        // 实现上传逻辑
    }
    
    // ... 实现其他方法
}
```

### 步骤 2: 注册 SPI 服务

在项目的 `src/main/resources/META-INF/services/` 目录下创建文件:

**文件名**: `com.apex.scaffold.common.spi.StorageProvider`

**内容**:
```
com.example.storage.AliOssStorageProvider
```

如果有多个实现,每行一个:
```
com.example.storage.AliOssStorageProvider
com.example.storage.S3StorageProvider
```

### 步骤 3: 打包为 JAR

将实现类和 SPI 注册文件打包为独立的 JAR 文件:

```bash
mvnd package
```

### 步骤 4: 添加到项目依赖

在 `pom.xml` 中添加依赖:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>ali-oss-storage-provider</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 步骤 5: 配置参数

在 `application.yml` 中添加存储提供者的配置参数。

## 运行时加载

应用启动时,ServiceLoader 会自动扫描 classpath 下所有 `META-INF/services/com.apex.scaffold.common.spi.StorageProvider` 文件中注册的实现类,并实例化它们。

可以通过以下方式验证加载情况:

```java
@Service
public class SpiDemoRunner implements CommandLineRunner {
    
    @Override
    public void run(String... args) {
        ServiceLoader<StorageProvider> loader = ServiceLoader.load(StorageProvider.class);
        for (StorageProvider provider : loader) {
            System.out.println("已加载存储提供者: " + provider.getProviderName());
        }
    }
}
```

输出示例（仅显示第三方扩展）:
```
已加载存储提供者: oss
已加载存储提供者: s3
```

## 注意事项

1. **线程安全**: StorageProvider 实现应该是线程安全的,因为可能被多个线程并发调用。

2. **资源管理**: 在 `destroy()` 方法中正确释放资源(如关闭连接池、清理缓存等)。

3. **异常处理**: 所有方法都应该妥善处理异常情况,返回有意义的错误信息。

4. **配置读取**: 建议从 Spring Environment 或系统属性读取配置,而不是硬编码。

5. **日志记录**: 关键操作应该记录日志,便于问题排查。

## 故障排查

### 问题 1: SPI 实现未被加载

**检查项**:
- 确认 `META-INF/services/com.apex.scaffold.common.spi.StorageProvider` 文件存在且路径正确
- 确认文件中列出的类名完全正确(包括包名)
- 确认实现类实现了 `StorageProvider` 接口
- 确认 JAR 包已正确添加到 classpath

### 问题 2: 初始化失败

**检查项**:
- 查看日志中的错误信息
- 确认配置参数是否正确
- 确认网络连接正常(对于云存储)
- 确认访问密钥有效

### 问题 3: 运行时异常

**检查项**:
- 查看完整的异常堆栈
- 确认存储桶是否存在
- 确认权限配置正确
- 确认文件大小和类型符合限制

## 最佳实践

1. **单一职责**: 每个 StorageProvider 只负责一种存储后端的适配。

2. **配置外部化**: 所有配置参数都应该通过配置文件或环境变量注入,不要硬编码。

3. **优雅降级**: 当某个存储提供者不可用时,应该能够降级到其他可用的提供者。

4. **性能优化**: 对于频繁调用的操作(如上传、下载),应该考虑连接池、缓存等优化手段。

5. **文档完善**: 为每个自定义的 StorageProvider 编写详细的使用文档。

## 参考资源

- [Java ServiceLoader 官方文档](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html)
- [MinIO Java SDK](https://docs.min.io/docs/java-client-quickstart-guide)
- [火山引擎 TOS Java SDK](https://www.volcengine.com/docs/6349/74857)

---

**作者**: Apex Team  
**版本**: 1.0.0  
**最后更新**: 2026-08-25

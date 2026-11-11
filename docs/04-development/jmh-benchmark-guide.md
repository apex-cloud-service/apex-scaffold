# JMH性能基准测试指南

## 概述

Apex Scaffold 从 v1.2.0 开始集成 JMH (Java Microbenchmark Harness) 用于性能基准测试。JMH 是 OpenJDK 提供的专业微基准测试工具,能够准确测量 Java 代码的性能表现,避免常见的基准测试陷阱(如 JIT 编译优化、死代码消除等)。

## 已实现的基准测试

### 1. DataMaskBenchmark - 数据脱敏性能测试

**位置**: `apex-common/src/test/java/com/apex/scaffold/common/benchmark/DataMaskBenchmark.java`

**测试内容**:
- 手机号脱敏 (`maskPhone`)
- 身份证号脱敏 (`maskIdCard`)
- 邮箱脱敏 (`maskEmail`)
- 姓名脱敏 (`maskName`)
- 银行卡号脱敏 (`maskBankCard`)
- 综合脱敏 (`maskAll`)

**运行方式**（JMH 依赖为 test scope，当前 POM 未配置 jmh-maven-plugin/shade，
`mvnd package` **不会**产出 `target/benchmarks.jar`；实际运行入口是该类的 `main()`）:
```bash
# 编译测试类（-DskipTests 只跳过执行、不跳过编译）
mvnd -pl apex-common -am test-compile

# 运行：在 IDE 中直接运行 DataMaskBenchmark#main，
# 或自行临时添加 shade/jmh-maven-plugin 配置后再 `java -jar`；
# main() 内已内置预热/测量轮数与线程参数
```

### 2. RedisBenchmark - Redis缓存操作性能测试

**位置**: `apex-infrastructure-cache/src/test/java/com/apex/scaffold/infrastructure/cache/benchmark/RedisBenchmark.java`

**测试内容**:
- 字符串写入 (`testStringSet`)
- 字符串读取 (`testStringGet`)
- 哈希表写入 (`testHashPut`)
- 哈希表读取 (`testHashGet`)
- 列表写入 (`testListPush`)
- 列表读取 (`testListRange`)
- 删除操作 (`testDelete`)

**运行前提**:
```bash
# 启动本地Redis服务器
podman run -d -p 6379:6379 docker.io/library/redis:7-alpine

# 或安装原生Redis
brew install redis
redis-server
```

**运行方式**（同上：`benchmarks.jar` 不由当前构建产出，经 `RedisBenchmark#main` 运行）:
```bash
# 编译测试类
mvnd -pl apex-infrastructure-cache -am test-compile

# 运行：在 IDE 中直接运行 RedisBenchmark#main
```

## 如何添加新的基准测试

### 步骤 1: 创建基准测试类

在对应模块的 `src/test/java` 目录下创建新的基准测试类:

```java
package com.apex.scaffold.common.benchmark;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@Threads(4)
public class MyBenchmark {
    
    @Setup(Level.Iteration)
    public void setUp() {
        // 初始化测试数据
    }
    
    @Benchmark
    public String testMyMethod() {
        // 待测试的方法
        return "result";
    }
}
```

### 步骤 2: 添加Maven依赖

确保模块的 `pom.xml` 中包含 JMH 依赖:

```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
```

### 步骤 3: 配置 jmh-maven-plugin

在模块的 `pom.xml` 中添加 JMH Maven 插件:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.openjdk.jmh</groupId>
            <artifactId>jmh-maven-plugin</artifactId>
            <version>0.13</version>
            <executions>
                <execution>
                    <goals>
                        <goal>benchmark</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 步骤 4: 运行基准测试

```bash
# 当前构建未产出 benchmarks.jar，运行入口为基准测试类的 main()（IDE 直接运行）
```

## JMH注解说明

### 核心注解

| 注解 | 作用 | 示例 |
|------|------|------|
| `@Benchmark` | 标记基准测试方法 | `@Benchmark public void test()` |
| `@State` | 定义状态对象的作用域 | `@State(Scope.Thread)` |
| `@Setup` | 初始化方法 | `@Setup(Level.Iteration)` |
| `@TearDown` | 清理方法 | `@TearDown(Level.Iteration)` |

### 配置注解

| 注解 | 参数 | 说明 |
|------|------|------|
| `@Warmup` | iterations, time, timeUnit | 预热迭代次数和时间 |
| `@Measurement` | iterations, time, timeUnit | 正式测量迭代次数和时间 |
| `@Fork` | value | 分叉进程数(隔离JVM状态) |
| `@Threads` | value | 并发线程数 |
| `@BenchmarkMode` | Mode | 基准测试模式(Throughput/AverageTime等) |
| `@OutputTimeUnit` | TimeUnit | 输出时间单位 |

### State作用域

- **Scope.Thread**: 每个线程独立的状态对象(推荐用于大多数场景)
- **Scope.Group**: 同一组内的线程共享状态对象
- **Scope.Benchmark**: 所有线程共享同一个状态对象

### Setup/TearDown级别

- **Level.Trial**: 整个基准测试前后各执行一次
- **Level.Iteration**: 每次迭代前后各执行一次
- **Level.Invocation**: 每次方法调用前后各执行一次(慎用,影响性能)

## 基准测试最佳实践

### 1. 避免常见陷阱

❌ **错误做法**:
```java
@Benchmark
public void badBenchmark() {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
        list.add("item");
    }
}
```
问题: JIT编译器可能检测到结果未被使用而优化掉整个循环。

✅ **正确做法**:
```java
@Benchmark
public int goodBenchmark() {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
        list.add("item");
    }
    return list.size(); // 返回结果防止被优化
}
```

### 2. 合理设置预热和测量参数

- **预热(Warmup)**: 让JVM充分编译和优化代码,通常3-5次迭代
- **测量(Measurement)**: 收集真实性能数据,通常5-10次迭代
- **分叉(Fork)**: 每次分叉启动新的JVM,隔离前一次测试的影响,通常2-3次

### 3. 选择合适的基准测试模式

| 模式 | 适用场景 | 输出含义 |
|------|----------|----------|
| Throughput | 吞吐量敏感的场景 | ops/s (每秒操作数) |
| AverageTime | 延迟敏感的场景 | s/op (每次操作耗时) |
| SampleTime | 需要采样分布的场景 | 采样统计信息 |
| SingleShotTime | 一次性操作的冷启动性能 | ms (单次耗时) |

### 4. 控制并发线程数

- **单线程**: 测试基础性能,排除锁竞争影响
- **多线程**: 测试并发场景下的性能表现
- 建议从单线程开始,逐步增加线程数观察性能变化

### 5. 隔离外部依赖

- 数据库、网络IO等外部依赖应该Mock或使用嵌入式版本
- 确保测试结果的可重复性和稳定性
- 记录测试环境配置(JVM版本、CPU、内存等)

## 结果解读

### 典型输出示例

```
Benchmark                    Mode  Cnt     Score     Error  Units
DataMaskBenchmark.testMaskPhone  thrpt   15  1234567.890 ± 12345.678  ops/s
DataMaskBenchmark.testMaskEmail  thrpt   15   987654.321 ±  9876.543  ops/s
```

**解读**:
- **Score**: 平均每秒操作次数(越高越好)
- **Error**: 标准误差(越小表示结果越稳定)
- **Units**: 单位(ops/s表示每秒操作数)

### 性能对比

比较不同实现的性能时,关注:
1. **Score差异**: 数值差距是否显著(超过Error范围)
2. **Error大小**: 误差过大说明测试不稳定,需要增加迭代次数
3. **趋势一致性**: 多次运行结果是否一致

## 持续集成中的基准测试

### Jenkins/GitLab CI配置示例

```yaml
benchmark:
  stage: test
  script:
    - mvnd clean package -pl apex-common -am -DskipTests
    - java -jar apex-common/target/benchmarks.jar -rf json -rff benchmark-results.json
  artifacts:
    reports:
      junit: benchmark-results.json
```

### 性能回归检测

可以编写脚本自动解析JMH输出,检测性能回归:

```bash
#!/bin/bash
# 检查性能是否下降超过10%
THRESHOLD=0.9
PREVIOUS_SCORE=$(cat previous-benchmark.txt | grep "testMaskPhone" | awk '{print $4}')
CURRENT_SCORE=$(cat current-benchmark.txt | grep "testMaskPhone" | awk '{print $4}')

RATIO=$(echo "$CURRENT_SCORE / $PREVIOUS_SCORE" | bc -l)
if (( $(echo "$RATIO < $THRESHOLD" | bc -l) )); then
    echo "性能回归警告: 当前性能为之前的 ${RATIO}%"
    exit 1
fi
```

## 常见问题

### Q1: 为什么我的基准测试结果波动很大?

**A**: 可能原因:
- 预热次数不足,JVM未充分编译优化
- 系统负载不稳定(其他进程占用资源)
- GC活动干扰(尝试增加堆内存 `-Xmx4g`)
- 测试数据量太小,受随机因素影响大

**解决方案**: 增加 `-wi`(预热迭代)、`-i`(测量迭代)、`-f`(分叉次数)

### Q2: JMH和JUnit有什么区别?

**A**: 
- **JUnit**: 功能测试框架,验证代码正确性
- **JMH**: 性能测试框架,测量代码执行效率
- JMH专门解决了JIT编译、死代码消除等性能测试陷阱

### Q3: 可以在生产环境运行JMH吗?

**A**: 不建议。JMH会消耗大量CPU和内存资源,影响正常业务。应该在独立的测试环境中运行。

## 参考资源

- [JMH官方文档](https://github.com/openjdk/jmh)
- [JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples)
- [Java Performance Tuning Guide](https://java-performance.info/)

---

**作者**: Apex Team  
**版本**: 1.0.0  
**最后更新**: 2026-08-25

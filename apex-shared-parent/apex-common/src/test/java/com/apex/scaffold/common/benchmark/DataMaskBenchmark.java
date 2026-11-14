package com.apex.scaffold.common.benchmark;

import com.apex.scaffold.common.util.DataMaskUtil;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * 数据脱敏工具性能基准测试。
 *
 * <p>该基准测试评估 {@link DataMaskUtil} 在不同脱敏场景下的性能表现,
 * 包括手机号、身份证、邮箱、姓名等常见数据类型的脱敏操作。</p>
 *
 * <h2>运行方式</h2>
 * <pre>{@code
 * # Maven方式
 * mvn clean package -pl apex-common -am
 * java -jar apex-common/target/benchmarks.jar "DataMaskBenchmark"
 *
 * # 指定迭代次数和线程数
 * java -jar apex-common/target/benchmarks.jar "DataMaskBenchmark" \
 *   -wi 5 -i 10 -f 3 -t 4
 * }</pre>
 *
 * <h2>测试结果解读</h2>
 * <ul>
 *   <li><b>Score</b>: 每秒操作次数(ops/s),越高越好</li>
 *   <li><b>Error</b>: 标准误差,越小表示结果越稳定</li>
 *   <li><b>Units</b>: 时间单位,通常为 ops/s</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.0.0
 * @see DataMaskUtil
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@Threads(4)
public class DataMaskBenchmark {

    /**
     * 测试用手机号。
     */
    private String phoneNumber;

    /**
     * 测试用身份证号。
     */
    private String idCard;

    /**
     * 测试用邮箱地址。
     */
    private String email;

    /**
     * 测试用姓名。
     */
    private String name;

    /**
     * 测试用银行卡号。
     */
    private String bankCard;

    /**
     * 初始化测试数据。
     *
     * @throws Exception 初始化失败时抛出异常
     */
    @Setup(Level.Iteration)
    public void setUp() throws Exception {
        this.phoneNumber = "13812345678";
        this.idCard = "110101199001011234";
        this.email = "zhangsan@example.com";
        this.name = "张三丰";
        this.bankCard = "6222021234567890123";
    }

    /**
     * 基准测试: 手机号脱敏性能。
     *
     * <p>测试将手机号中间4位替换为星号的性能。</p>
     *
     * @return 脱敏后的手机号字符串
     */
    @Benchmark
    public String testMaskPhone() {
        return DataMaskUtil.maskPhone(phoneNumber);
    }

    /**
     * 基准测试: 身份证号脱敏性能。
     *
     * <p>测试将身份证号中间8位替换为星号的性能。</p>
     *
     * @return 脱敏后的身份证号字符串
     */
    @Benchmark
    public String testMaskIdCard() {
        return DataMaskUtil.maskIdCard(idCard);
    }

    /**
     * 基准测试: 邮箱脱敏性能。
     *
     * <p>测试将邮箱用户名部分替换为星号的性能。</p>
     *
     * @return 脱敏后的邮箱地址字符串
     */
    @Benchmark
    public String testMaskEmail() {
        return DataMaskUtil.maskEmail(email);
    }

    /**
     * 基准测试: 姓名脱敏性能。
     *
     * <p>测试将姓名中间字符替换为星号的性能。</p>
     *
     * @return 脱敏后的姓名字符串
     */
    @Benchmark
    public String testMaskName() {
        return DataMaskUtil.maskName(name);
    }

    /**
     * 基准测试: 银行卡号脱敏性能。
     *
     * <p>测试将银行卡号中间12位替换为星号的性能。</p>
     *
     * @return 脱敏后的银行卡号字符串
     */
    @Benchmark
    public String testMaskBankCard() {
        return DataMaskUtil.maskBankCard(bankCard);
    }

    /**
     * 基准测试: 综合脱敏性能。
     *
     * <p>测试连续执行多种脱敏操作的综合性能。</p>
     *
     * @return 脱敏后的组合字符串
     */
    @Benchmark
    public String testMaskAll() {
        StringBuilder sb = new StringBuilder();
        sb.append(DataMaskUtil.maskPhone(phoneNumber)).append(",");
        sb.append(DataMaskUtil.maskIdCard(idCard)).append(",");
        sb.append(DataMaskUtil.maskEmail(email)).append(",");
        sb.append(DataMaskUtil.maskName(name)).append(",");
        sb.append(DataMaskUtil.maskBankCard(bankCard));
        return sb.toString();
    }

    /**
     * 主方法,用于直接运行基准测试。
     *
     * @param args 命令行参数
     * @throws Exception 运行失败时抛出异常
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(DataMaskBenchmark.class.getSimpleName())
                .forks(3)
                .threads(4)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }
}

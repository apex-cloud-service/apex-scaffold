package com.apex.scaffold.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 代码生成器命令行入口。
 *
 * <p>读取 JSON 表结构定义文件，按指定迁移版本号生成 DDD 五层代码到
 * 输出目录。供 {@code scripts/codegen/generate.sh} 调用。</p>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * java com.apex.scaffold.generator.GeneratorCli -i definition.json -o ./generated -v 3
 * java com.apex.scaffold.generator.GeneratorCli -i definition.json -o ./generated -f /path/to/apex-admin-web
 * }</pre>
 *
 * <ul>
 *   <li>{@code -i} 表结构定义 JSON 文件路径（必填）</li>
 *   <li>{@code -o} 后端输出根目录（缺省 {@code ./generated}）</li>
 *   <li>{@code -v} 迁移脚本版本号（缺省 2）</li>
 *   <li>{@code -f} 前端工程根目录（指定后同时生成 Vue3 管理页面，缺省不生成）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.0.0
 * @see CodeGenerator
 * @see TableDefinition
 */
@Slf4j
public final class GeneratorCli {

    /**
     * 命令行参数容器，供参数分派 Lambda 变更。
     */
    private static final class Args {
        String inputFile;
        String outputDir = "./generated";
        String frontendDir;
        int migrationVersion = 2;
    }

    /**
     * 私有构造函数，禁止实例化。
     */
    private GeneratorCli() {}

    /**
     * 命令行入口。
     *
     * <p>参数通过 {@link Map} 分发表按选项名定位，{@link #parseArgs(String[])} 完成收集
     * 后交给 {@link #run(Args)} 执行生成。</p>
     *
     * @param args 命令行参数（{@code -i} / {@code -o} / {@code -v} / {@code -f}）
     */
    public static void main(String[] args) {
        Args parsed = parseArgs(args);
        if (parsed.inputFile == null) {
            printUsage();
            System.exit(2);
        }
        try {
            run(parsed);
        } catch (Exception e) {
            System.err.println("代码生成失败: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 解析命令行参数为 {@link Args} 容器。
     *
     * <p>{@code -i/-o/-v/-f} 各选项按名称分派到对应的赋值 Lambda；未知参数或缺少
     * 选项值时打印用法并以退出码 2 结束进程。</p>
     *
     * @param args 命令行参数
     * @return 解析后的参数容器；用例未提供 {@code -i} 时其 {@code inputFile} 为 {@code null}
     */
    private static Args parseArgs(String[] args) {
        Args parsed = new Args();
        Map<String, Consumer<String>> dispatcher = new LinkedHashMap<>();
        dispatcher.put("-i", value -> parsed.inputFile = value);
        dispatcher.put("-o", value -> parsed.outputDir = value);
        dispatcher.put("-f", value -> parsed.frontendDir = value);
        dispatcher.put("-v", value -> parsed.migrationVersion = Integer.parseInt(value));

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            Consumer<String> setter = dispatcher.get(argument);
            if (setter != null) {
                if (i + 1 >= args.length) {
                    System.err.println("缺少参数值: " + argument);
                    printUsage();
                    System.exit(2);
                }
                setter.accept(args[++i]);
            } else {
                System.err.println("未知参数: " + argument);
                printUsage();
                System.exit(2);
            }
        }
        return parsed;
    }

    /**
     * 依据解析后的参数执行代码生成。
     *
     * @param parsed 已解析的命令行参数
     * @throws Exception 读取定义、生成代码或校验失败
     */
    private static void run(Args parsed) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TableDefinition definition =
                mapper.readValue(Files.readAllBytes(Paths.get(parsed.inputFile)), TableDefinition.class);
        if (definition.getEntityName() == null || definition.getEntityName().isBlank()) {
            throw new IllegalArgumentException("entityName 不能为空");
        }
        validateFields(definition);

        CodeGenerator generator = new CodeGenerator();
        Path outputRoot = Paths.get(parsed.outputDir).toAbsolutePath().normalize();
        var files = generator.generate(definition, outputRoot, parsed.migrationVersion);

        System.out.println("=== 后端代码生成完成，共 " + files.size() + " 个文件 ===");
        files.forEach(file -> System.out.println("  " + file));
        System.out.println("输出目录: " + outputRoot);

        // 可选：同时生成 Vue3 管理前端页面
        if (parsed.frontendDir != null && !parsed.frontendDir.isBlank()) {
            Path frontendRoot = Paths.get(parsed.frontendDir).toAbsolutePath().normalize();
            var frontendFiles = generator.generateFrontend(definition, frontendRoot);
            System.out.println("=== 前端代码生成完成，共 " + frontendFiles.size() + " 个文件 ===");
            frontendFiles.forEach(file -> System.out.println("  " + file));
            System.out.println("前端输出目录: " + frontendRoot);
        }
    }

    /**
     * 校验字段定义合法性。
     *
     * @param definition 表结构定义
     * @throws IllegalArgumentException 当字段非法时抛出
     */
    private static void validateFields(TableDefinition definition) {
        if (definition.getFields() == null || definition.getFields().isEmpty()) {
            throw new IllegalArgumentException("fields 不能为空，至少需要一个业务字段");
        }
        for (FieldDefinition field : definition.getFields()) {
            if (field.getName() == null || field.getName().isBlank()) {
                throw new IllegalArgumentException("字段名不能为空");
            }
            if (field.getType() == null || field.getType().isBlank()) {
                throw new IllegalArgumentException("字段 " + field.getName() + " 的 type 不能为空");
            }
        }
    }

    /**
     * 打印用法说明。
     */
    private static void printUsage() {
        System.out.println(
                "用法: GeneratorCli -i <definition.json> [-o <outputDir>] [-v <migrationVersion>] [-f <frontendDir>]");
    }
}

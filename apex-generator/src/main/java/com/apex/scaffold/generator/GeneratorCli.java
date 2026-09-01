package com.apex.scaffold.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * 私有构造函数，禁止实例化。
     */
    private GeneratorCli() {}

    /**
     * 命令行入口。
     *
     * @param args 命令行参数（{@code -i} / {@code -o} / {@code -v} / {@code -f}）
     */
    public static void main(String[] args) {
        String inputFile = null;
        String outputDir = "./generated";
        String frontendDir = null;
        int migrationVersion = 2;

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if ("-i".equals(argument)) {
                inputFile = args[++i];
            } else if ("-o".equals(argument)) {
                outputDir = args[++i];
            } else if ("-v".equals(argument)) {
                migrationVersion = Integer.parseInt(args[++i]);
            } else if ("-f".equals(argument)) {
                frontendDir = args[++i];
            } else {
                System.err.println("未知参数: " + argument);
                printUsage();
                System.exit(2);
            }
        }

        if (inputFile == null) {
            printUsage();
            System.exit(2);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            TableDefinition definition =
                    mapper.readValue(Files.readAllBytes(Paths.get(inputFile)), TableDefinition.class);
            if (definition.getEntityName() == null || definition.getEntityName().isBlank()) {
                throw new IllegalArgumentException("entityName 不能为空");
            }
            validateFields(definition);

            CodeGenerator generator = new CodeGenerator();
            Path outputRoot = Paths.get(outputDir).toAbsolutePath().normalize();
            var files = generator.generate(definition, outputRoot, migrationVersion);

            System.out.println("=== 后端代码生成完成，共 " + files.size() + " 个文件 ===");
            files.forEach(file -> System.out.println("  " + file));
            System.out.println("输出目录: " + outputRoot);

            // 可选：同时生成 Vue3 管理前端页面
            if (frontendDir != null && !frontendDir.isBlank()) {
                Path frontendRoot = Paths.get(frontendDir).toAbsolutePath().normalize();
                var frontendFiles = generator.generateFrontend(definition, frontendRoot);
                System.out.println("=== 前端代码生成完成，共 " + frontendFiles.size() + " 个文件 ===");
                frontendFiles.forEach(file -> System.out.println("  " + file));
                System.out.println("前端输出目录: " + frontendRoot);
            }
        } catch (Exception e) {
            System.err.println("代码生成失败: " + e.getMessage());
            System.exit(1);
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

package com.apex.scaffold.auth.infrastructure.security;

import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置加密命令行工具
 *
 * <p>用于生成加密密钥和加密敏感配置值。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * # 1. 生成新的加密密钥
 * java -cp apex-auth-service.jar com.apex.scaffold.auth.infrastructure.security.ConfigEncryptionCLI generateKey
 *
 * # 2. 加密配置值
 * java -cp apex-auth-service.jar com.apex.scaffold.auth.infrastructure.security.ConfigEncryptionCLI encrypt <密钥> <明文>
 *
 * # 3. 解密配置值
 * java -cp apex-auth-service.jar com.apex.scaffold.auth.infrastructure.security.ConfigEncryptionCLI decrypt <密钥> <密文>
 * }</pre>
 *
 * @author Apex Security Team
 * @since 1.3.1
 */
@Slf4j
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class ConfigEncryptionCLI {

    private static final int MIN_ARGS_FOR_OPERATION = 3;
    private static final String COMMAND_GENERATE = "generate";
    private static final String COMMAND_ENCRYPT = "encrypt";
    private static final String COMMAND_DECRYPT = "decrypt";
    private static final String COMMAND_INTERACTIVE = "interactive";
    private static final String CHOICE_GENERATE = "1";
    private static final String CHOICE_ENCRYPT = "2";
    private static final String CHOICE_DECRYPT = "3";

    /**
     * 命令行工具入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        if (COMMAND_GENERATE.equals(command)) {
            generateKey();
        } else if (COMMAND_ENCRYPT.equals(command)) {
            requireOperationArguments(args, "encrypt <密钥Base64> <明文>");
            encrypt(args[1], args[2]);
        } else if (COMMAND_DECRYPT.equals(command)) {
            requireOperationArguments(args, "decrypt <密钥Base64> <密文>");
            decrypt(args[1], args[2]);
        } else if (COMMAND_INTERACTIVE.equals(command)) {
            interactiveMode();
        } else {
            System.err.println("错误: 未知命令 '" + command + "'");
            printUsage();
            System.exit(1);
        }
    }

    private static void requireOperationArguments(String[] args, String usage) {
        if (args.length < MIN_ARGS_FOR_OPERATION) {
            System.err.println("错误: 缺少参数");
            System.err.println("用法: " + usage);
            System.exit(1);
        }
    }

    private static void generateKey() {
        System.out.println("=== 生成配置加密密钥 ===\n");

        String keyBase64 = ConfigEncryptionService.generateKeyBase64();

        System.out.println("新密钥已生成（Base64编码）：");
        System.out.println(keyBase64);
        System.out.println();
        System.out.println("请将此密钥保存到安全位置，并通过以下方式之一配置：");
        System.out.println();
        System.out.println("1. 环境变量：");
        System.out.println("   export APEX_SECURITY_CONFIG_ENCRYPTION_KEY=" + keyBase64);
        System.out.println();
        System.out.println("2. application.yml：");
        System.out.println("   apex:");
        System.out.println("     security:");
        System.out.println("       config-encryption-key: " + keyBase64);
        System.out.println();
        System.out.println("3. 启动参数：");
        System.out.println("   java -Dapex.security.config-encryption-key=" + keyBase64 + " -jar app.jar");
        System.out.println();
        System.out.println("⚠️  警告：请妥善保管此密钥，密钥丢失将无法解密配置！");
    }

    private static void encrypt(String keyBase64, String plaintext) {
        try {
            System.out.println("=== 加密配置值 ===\n");

            ConfigEncryptionService service = createService(keyBase64);
            String encrypted = service.encrypt(plaintext);

            System.out.println("明文：");
            System.out.println(plaintext);
            System.out.println();
            System.out.println("密文（Base64）：");
            System.out.println(encrypted);
            System.out.println();
            System.out.println("在配置文件中使用：");
            System.out.println("ENC(" + encrypted + ")");
            System.out.println();
            System.out.println("示例：");
            System.out.println("spring:");
            System.out.println("  datasource:");
            System.out.println("    password: ENC(" + encrypted + ")");

        } catch (Exception e) {
            System.err.println("加密失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void decrypt(String keyBase64, String ciphertext) {
        try {
            System.out.println("=== 解密配置值 ===\n");

            ConfigEncryptionService service = createService(keyBase64);

            // 如果密文是ENC()格式，提取真实密文
            if (ConfigEncryptionService.isEncrypted(ciphertext)) {
                ciphertext = ConfigEncryptionService.extractCiphertext(ciphertext);
            }

            String plaintext = service.decrypt(ciphertext);

            System.out.println("密文：");
            System.out.println(ciphertext);
            System.out.println();
            System.out.println("明文：");
            System.out.println(plaintext);

        } catch (Exception e) {
            System.err.println("解密失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void interactiveMode() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== 配置加密工具 - 交互模式 ===\n");

        // 询问是生成密钥还是使用现有密钥
        System.out.println("请选择操作：");
        System.out.println("1. 生成新密钥");
        System.out.println("2. 使用现有密钥加密配置");
        System.out.println("3. 使用现有密钥解密配置");
        System.out.print("\n请输入选项 (1-3): ");

        String choice = scanner.nextLine().trim();

        if (CHOICE_GENERATE.equals(choice)) {
            generateKey();
        } else if (CHOICE_ENCRYPT.equals(choice)) {
            System.out.print("\n请输入密钥（Base64）: ");
            String encryptKey = scanner.nextLine().trim();
            System.out.print("请输入要加密的明文: ");
            String plaintext = scanner.nextLine().trim();
            encrypt(encryptKey, plaintext);
        } else if (CHOICE_DECRYPT.equals(choice)) {
            System.out.print("\n请输入密钥（Base64）: ");
            String decryptKey = scanner.nextLine().trim();
            System.out.print("请输入要解密的密文: ");
            String ciphertext = scanner.nextLine().trim();
            decrypt(decryptKey, ciphertext);
        } else {
            System.err.println("无效的选项");
            System.exit(1);
        }

        scanner.close();
    }

    private static ConfigEncryptionService createService(String keyBase64) {
        return new ConfigEncryptionService(keyBase64);
    }

    private static void printUsage() {
        System.out.println("配置加密工具 - 用于加密敏感配置值");
        System.out.println();
        System.out.println("用法：");
        System.out.println("  java -cp apex-auth-service.jar " + ConfigEncryptionCLI.class.getName() + " <命令> [参数]");
        System.out.println();
        System.out.println("命令：");
        System.out.println("  generateKey                生成新的加密密钥");
        System.out.println("  encrypt <密钥> <明文>       加密配置值");
        System.out.println("  decrypt <密钥> <密文>       解密配置值");
        System.out.println("  interactive                交互模式");
        System.out.println();
        System.out.println("示例：");
        System.out.println("  # 生成密钥");
        System.out.println("  java -cp apex-auth-service.jar " + ConfigEncryptionCLI.class.getName() + " generateKey");
        System.out.println();
        System.out.println("  # 加密密码");
        System.out.println("  java -cp apex-auth-service.jar " + ConfigEncryptionCLI.class.getName()
                + " encrypt <密钥> myPassword123");
        System.out.println();
        System.out.println("  # 交互模式");
        System.out.println("  java -cp apex-auth-service.jar " + ConfigEncryptionCLI.class.getName() + " interactive");
    }
}

package com.apex.scaffold.tools;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Scanner;

/**
 * 配置加密命令行工具
 * 
 * 用法：
 * 1. 生成密钥：java ConfigEncryptionTool generate-key
 * 2. 加密配置：java ConfigEncryptionTool encrypt <plaintext> <key>
 * 3. 解密配置：java ConfigEncryptionTool decrypt <ciphertext> <key>
 * 4. 交互式模式：java ConfigEncryptionTool interactive <key>
 * 
 * 示例：
 * java ConfigEncryptionTool generate-key
 * java ConfigEncryptionTool encrypt "MyPassword123" "01234567890123456789012345678901"
 * java ConfigEncryptionTool decrypt "ENC(base64...)" "01234567890123456789012345678901"
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.0
 * @created 2026-10-04
 */
public class ConfigEncryptionTool {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int KEY_LENGTH_BYTE = 32;
    
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    /**
     * 命令行入口：按 args[0] 分发到 generate-key / encrypt / decrypt / interactive 子命令。
     *
     * @param args 子命令与参数；无参数时打印用法
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "generate-key":
                    generateKey();
                    break;
                    
                case "encrypt":
                    if (args.length < 3) {
                        System.err.println("错误：缺少参数");
                        System.err.println("用法：java ConfigEncryptionTool encrypt <plaintext> <key>");
                        System.exit(1);
                    }
                    String plaintext = args[1];
                    String encKey = args[2];
                    String encrypted = encrypt(plaintext, encKey);
                    System.out.println("\n✅ 加密成功！");
                    System.out.println("明文: " + plaintext);
                    System.out.println("密文: " + encrypted);
                    System.out.println("\n💡 请将以下内容复制到配置文件中：");
                    System.out.println(encrypted);
                    break;
                    
                case "decrypt":
                    if (args.length < 3) {
                        System.err.println("错误：缺少参数");
                        System.err.println("用法：java ConfigEncryptionTool decrypt <ciphertext> <key>");
                        System.exit(1);
                    }
                    String ciphertext = args[1];
                    String decKey = args[2];
                    String decrypted = decrypt(ciphertext, decKey);
                    System.out.println("\n✅ 解密成功！");
                    System.out.println("密文: " + ciphertext);
                    System.out.println("明文: " + decrypted);
                    break;
                    
                case "interactive":
                    if (args.length < 2) {
                        System.err.println("错误：缺少密钥参数");
                        System.err.println("用法：java ConfigEncryptionTool interactive <key>");
                        System.exit(1);
                    }
                    String key = args[1];
                    runInteractive(key);
                    break;
                    
                case "help":
                case "-h":
                case "--help":
                    printUsage();
                    break;
                    
                default:
                    System.err.println("错误：未知命令 '" + command + "'");
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("\n❌ 操作失败：" + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 生成随机密钥
     */
    private static void generateKey() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[KEY_LENGTH_BYTE];
        random.nextBytes(key);
        
        String hexKey = bytesToHex(key);
        
        System.out.println("\n🔑 已生成新的AES-256密钥：");
        System.out.println("\n十六进制格式（推荐用于CONFIG_ENCRYPTION_KEY）：");
        System.out.println(hexKey);
        System.out.println("\n💡 请将密钥保存到环境变量：");
        System.out.println("export CONFIG_ENCRYPTION_KEY=" + hexKey);
        System.out.println("\n⚠️  警告：请妥善保管密钥，丢失后无法解密！");
    }

    /**
     * 加密
     */
    private static String encrypt(String plaintext, String keyHex) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        // 验证密钥长度
        if (keyHex.length() != KEY_LENGTH_BYTE * 2) {
            throw new IllegalArgumentException(
                String.format("密钥长度必须为%d位十六进制字符（%d字节）", 
                    KEY_LENGTH_BYTE * 2, KEY_LENGTH_BYTE)
            );
        }
        
        byte[] key = hexToBytes(keyHex);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        
        // 生成随机IV
        byte[] iv = new byte[IV_LENGTH_BYTE];
        new SecureRandom().nextBytes(iv);
        
        // 初始化Cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
        
        // 加密
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // 组合 IV + 密文
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        
        // Base64编码并添加前缀后缀
        String encoded = Base64.getEncoder().encodeToString(combined);
        return ENC_PREFIX + encoded + ENC_SUFFIX;
    }

    /**
     * 解密
     */
    private static String decrypt(String ciphertext, String keyHex) throws Exception {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        
        // 移除前缀后缀
        String encoded = ciphertext;
        if (ciphertext.startsWith(ENC_PREFIX) && ciphertext.endsWith(ENC_SUFFIX)) {
            encoded = ciphertext.substring(ENC_PREFIX.length(), ciphertext.length() - ENC_SUFFIX.length());
        }
        
        // 验证密钥长度
        if (keyHex.length() != KEY_LENGTH_BYTE * 2) {
            throw new IllegalArgumentException(
                String.format("密钥长度必须为%d位十六进制字符（%d字节）", 
                    KEY_LENGTH_BYTE * 2, KEY_LENGTH_BYTE)
            );
        }
        
        byte[] key = hexToBytes(keyHex);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        
        // Base64解码
        byte[] combined = Base64.getDecoder().decode(encoded);
        
        // 分离IV和密文
        byte[] iv = new byte[IV_LENGTH_BYTE];
        byte[] ciphertextBytes = new byte[combined.length - IV_LENGTH_BYTE];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, IV_LENGTH_BYTE, ciphertextBytes, 0, ciphertextBytes.length);
        
        // 初始化Cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
        
        // 解密
        byte[] plaintext = cipher.doFinal(ciphertextBytes);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * 交互式模式
     */
    private static void runInteractive(String key) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n🔐 配置加密工具 - 交互式模式");
        System.out.println("=====================================");
        System.out.println("命令：");
        System.out.println("  e <明文>     - 加密");
        System.out.println("  d <密文>     - 解密");
        System.out.println("  q            - 退出");
        System.out.println("=====================================\n");
        
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            
            if (line.isEmpty()) {
                continue;
            }
            
            String[] parts = line.split(" ", 2);
            String cmd = parts[0].toLowerCase();
            
            if (cmd.equals("q") || cmd.equals("quit") || cmd.equals("exit")) {
                System.out.println("再见！");
                break;
            }
            
            if (parts.length < 2) {
                System.err.println("❌ 错误：缺少参数");
                continue;
            }
            
            String value = parts[1];
            
            try {
                switch (cmd) {
                    case "e":
                    case "encrypt":
                        String encrypted = encrypt(value, key);
                        System.out.println("✅ 加密结果：");
                        System.out.println(encrypted);
                        break;
                        
                    case "d":
                    case "decrypt":
                        String decrypted = decrypt(value, key);
                        System.out.println("✅ 解密结果：");
                        System.out.println(decrypted);
                        break;
                        
                    default:
                        System.err.println("❌ 未知命令：" + cmd);
                }
            } catch (Exception e) {
                System.err.println("❌ 操作失败：" + e.getMessage());
            }
            
            System.out.println();
        }
        
        scanner.close();
    }

    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("\n🔐 配置加密工具");
        System.out.println("=====================================");
        System.out.println("用法：");
        System.out.println("  java ConfigEncryptionTool <command> [args...]");
        System.out.println("\n命令：");
        System.out.println("  generate-key              生成新的AES-256密钥");
        System.out.println("  encrypt <明文> <密钥>     加密配置值");
        System.out.println("  decrypt <密文> <密钥>     解密配置值");
        System.out.println("  interactive <密钥>        进入交互式模式");
        System.out.println("  help                      显示此帮助信息");
        System.out.println("\n示例：");
        System.out.println("  # 生成密钥");
        System.out.println("  java ConfigEncryptionTool generate-key");
        System.out.println("\n  # 加密数据库密码");
        System.out.println("  java ConfigEncryptionTool encrypt \"MyPassword123\" \"01234567890123456789012345678901\"");
        System.out.println("\n  # 解密配置值");
        System.out.println("  java ConfigEncryptionTool decrypt \"ENC(...)\" \"01234567890123456789012345678901\"");
        System.out.println("\n  # 交互式模式");
        System.out.println("  java ConfigEncryptionTool interactive \"01234567890123456789012345678901\"");
        System.out.println("=====================================\n");
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 十六进制字符串转字节数组
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

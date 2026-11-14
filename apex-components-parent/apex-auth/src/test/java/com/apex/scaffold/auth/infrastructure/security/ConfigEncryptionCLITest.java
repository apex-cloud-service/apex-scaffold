package com.apex.scaffold.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigEncryptionCLI} 的单元测试类
 *
 * <p>测试配置加密CLI工具的核心命令：generate、encrypt、decrypt、interactive。
 *
 * <p>由于 {@link ConfigEncryptionCLI#main} 在参数错误时会调用 {@code System.exit}，
 * 本测试只覆盖不会触发 {@code System.exit} 的正常命令路径。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("ConfigEncryptionCLI 单元测试")
class ConfigEncryptionCLITest {

    private final PrintStream originalOut = System.out;
    private final InputStream originalIn = System.in;
    private ByteArrayOutputStream outputCapture;

    @BeforeEach
    void setUp() {
        outputCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputCapture, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    private String output() {
        return outputCapture.toString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("generate 命令")
    class GenerateCommandTests {

        @Test
        @DisplayName("应生成并打印Base64密钥")
        void shouldPrintGeneratedKey() {
            ConfigEncryptionCLI.main(new String[] {"generate"});

            String printed = output();
            assertThat(printed).contains("新密钥已生成");
            assertThat(printed).contains("apex.security.config-encryption-key");
        }
    }

    @Nested
    @DisplayName("encrypt 命令")
    class EncryptCommandTests {

        @Test
        @DisplayName("参数完整时应输出密文和ENC()包裹格式")
        void shouldEncryptAndPrintCiphertext() {
            String keyBase64 = ConfigEncryptionService.generateKeyBase64();

            ConfigEncryptionCLI.main(new String[] {"encrypt", keyBase64, "my-plain-secret"});

            String printed = output();
            assertThat(printed).contains("密文");
            assertThat(printed).contains("ENC(");
            assertThat(printed).contains("my-plain-secret");
        }
    }

    @Nested
    @DisplayName("decrypt 命令")
    class DecryptCommandTests {

        @Test
        @DisplayName("传入普通密文应正确解密并输出明文")
        void shouldDecryptRawCiphertext() {
            String keyBase64 = ConfigEncryptionService.generateKeyBase64();
            ConfigEncryptionService service = new ConfigEncryptionService(keyBase64);
            String ciphertext = service.encrypt("decrypt-me");

            ConfigEncryptionCLI.main(new String[] {"decrypt", keyBase64, ciphertext});

            String printed = output();
            assertThat(printed).contains("明文");
            assertThat(printed).contains("decrypt-me");
        }

        @Test
        @DisplayName("传入ENC()包裹格式密文应自动提取并解密")
        void shouldDecryptEncWrappedCiphertext() {
            String keyBase64 = ConfigEncryptionService.generateKeyBase64();
            ConfigEncryptionService service = new ConfigEncryptionService(keyBase64);
            String ciphertext = service.encrypt("wrapped-secret");
            String wrapped = "ENC(" + ciphertext + ")";

            ConfigEncryptionCLI.main(new String[] {"decrypt", keyBase64, wrapped});

            String printed = output();
            assertThat(printed).contains("wrapped-secret");
        }
    }

    @Nested
    @DisplayName("interactive 命令")
    class InteractiveCommandTests {

        @Test
        @DisplayName("选择选项1应触发密钥生成流程")
        void shouldGenerateKeyWhenChoiceIsOne() {
            System.setIn(new ByteArrayInputStream("1\n".getBytes(StandardCharsets.UTF_8)));

            ConfigEncryptionCLI.main(new String[] {"interactive"});

            String printed = output();
            assertThat(printed).contains("交互模式");
            assertThat(printed).contains("新密钥已生成");
        }

        @Test
        @DisplayName("选择选项2应触发加密流程并输出密文")
        void shouldEncryptWhenChoiceIsTwo() {
            String keyBase64 = ConfigEncryptionService.generateKeyBase64();
            String input = "2\n" + keyBase64 + "\n" + "interactive-secret" + "\n";
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

            ConfigEncryptionCLI.main(new String[] {"interactive"});

            String printed = output();
            assertThat(printed).contains("interactive-secret");
        }

        @Test
        @DisplayName("选择选项3应触发解密流程并输出明文")
        void shouldDecryptWhenChoiceIsThree() {
            String keyBase64 = ConfigEncryptionService.generateKeyBase64();
            ConfigEncryptionService service = new ConfigEncryptionService(keyBase64);
            String ciphertext = service.encrypt("interactive-decrypt");
            String input = "3\n" + keyBase64 + "\n" + ciphertext + "\n";
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

            ConfigEncryptionCLI.main(new String[] {"interactive"});

            String printed = output();
            assertThat(printed).contains("interactive-decrypt");
        }
    }
}

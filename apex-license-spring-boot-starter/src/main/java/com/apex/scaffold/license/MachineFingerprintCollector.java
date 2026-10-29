package com.apex.scaffold.license;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 机器指纹采集器。
 *
 * <p>采集三个维度的硬件标识并拼接后做 SHA-256 哈希，生成 64 字符十六进制指纹串，
 * 用于 License 机器绑定验证。指纹采集过程容错降级：任一维度采集失败时
 * 该维度回退为空字符串，不影响其他维度的采集和最终哈希生成。
 * CPU 与主板维度均通过 {@link OsFamily#detect()} 识别操作系统后按策略分派采集。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>MAC 地址</strong>：遍历 {@link NetworkInterface}，跳过 loopback/虚拟接口，
 *       收集所有物理网卡 MAC，按字典序拼接</li>
 *   <li><strong>CPU 标识</strong>：
 *       <ul>
 *         <li>Linux：读取 {@code /proc/cpuinfo} 的 {@code model name} 和 {@code physical id}</li>
 *         <li>macOS：执行 {@code sysctl -n machdep.cpu.brand_string}</li>
 *         <li>Windows：读取环境变量 {@code PROCESSOR_IDENTIFIER}</li>
 *       </ul>
 *   </li>
 *   <li><strong>主板序列号</strong>：
 *       <ul>
 *         <li>Linux：执行 {@code dmidecode -s baseboard-serial-number}</li>
 *         <li>macOS：执行 {@code system_profiler SPHardwareDataType} 并提取 Serial Number</li>
 *         <li>Windows：执行 {@code wmic baseboard get serialnumber}</li>
 *       </ul>
 *       该维度在容器（Docker/K8s）内通常不可用，会回退为空字符串。
 *   </li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>三个维度以 {@code |} 分隔拼接为 {@code mac=xxx|cpu=xxx|board=xxx} 格式，
 * 然后取 SHA-256 的十六进制小写表示（64 字符）。</p>
 *
 * <h2>设计说明</h2>
 * <p>容器内 {@code dmidecode} 通常不可用，主板维度回退为空。若容器内 MAC 地址
 * 每次重建都变化（如 Docker 默认 bridge 网络），建议通过
 * {@code apex.license.machine-fingerprint-override} 手动指定指纹。</p>
 *
 * @author Apex Team
 * @version 1.2.1
 * @since 1.2.1
 */
@Slf4j
public final class MachineFingerprintCollector {

    private static final String LINE_SEPARATOR = "\n";

    private static final String SEPARATOR = "|";

    private MachineFingerprintCollector() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 采集当前机器的指纹哈希。
     *
     * @return 64 字符十六进制 SHA-256 哈希；采集全部失败时返回空字符串
     */
    public static String collect() {
        String macPart = collectMacAddresses();
        String cpuPart = collectCpuId();
        String boardPart = collectBoardSerial();

        String raw = "mac=" + macPart + SEPARATOR + "cpu=" + cpuPart + SEPARATOR + "board=" + boardPart;
        log.debug("机器指纹原始数据: {}", raw);

        return sha256Hex(raw);
    }

    /**
     * 采集所有物理网卡的 MAC 地址（去重 + 字典序拼接）。
     *
     * @return MAC 地址列表用逗号拼接；无可用网卡时返回空字符串
     */
    private static String collectMacAddresses() {
        Set<String> macs = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint() || !ni.isUp()) {
                    continue;
                }
                byte[] hardwareAddress = ni.getHardwareAddress();
                if (hardwareAddress != null && hardwareAddress.length > 0) {
                    macs.add(HexFormat.of().formatHex(hardwareAddress));
                }
            }
        } catch (Exception e) {
            log.debug("MAC 地址采集失败: {}", e.getMessage());
        }
        return String.join(",", macs);
    }

    /**
     * 采集 CPU 标识。
     *
     * <p>根据操作系统类型选择采集方式：</p>
     * <ul>
     *   <li>Linux：读取 /proc/cpuinfo 的 model name 和 physical id 行</li>
     *   <li>macOS：执行 sysctl -n machdep.cpu.brand_string</li>
     *   <li>Windows：读取环境变量 PROCESSOR_IDENTIFIER</li>
     * </ul>
     *
     * @return CPU 标识字符串；采集失败时返回空字符串
     */
    private static String collectCpuId() {
        try {
            return switch (OsFamily.detect()) {
                case LINUX -> collectCpuIdLinux();
                case MAC -> collectCpuIdMac();
                case WINDOWS -> System.getenv("PROCESSOR_IDENTIFIER");
                case UNKNOWN -> "";
            };
        } catch (Exception e) {
            log.debug("CPU 标识采集失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * Linux 下从 /proc/cpuinfo 读取 CPU 标识。
     */
    private static String collectCpuIdLinux() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                java.nio.file.Files.newInputStream(java.nio.file.Paths.get("/proc/cpuinfo")),
                StandardCharsets.UTF_8))) {
            String line;
            boolean modelNameFound = false;
            boolean physicalIdFound = false;
            while ((line = reader.readLine()) != null) {
                if (!modelNameFound && line.startsWith("model name")) {
                    sb.append(line.substring(line.indexOf(':') + 1).trim());
                    modelNameFound = true;
                }
                if (!physicalIdFound && line.startsWith("physical id")) {
                    sb.append(":").append(line.substring(line.indexOf(':') + 1).trim());
                    physicalIdFound = true;
                }
                if (modelNameFound && physicalIdFound) {
                    break;
                }
            }
        }
        return sb.toString();
    }

    /**
     * macOS 下通过 sysctl 读取 CPU 品牌字符串。
     */
    private static String collectCpuIdMac() throws IOException, InterruptedException {
        return execCommand("sysctl", "-n", "machdep.cpu.brand_string").trim();
    }

    /**
     * 采集主板序列号。
     *
     * <p>根据操作系统选择采集命令；容器内通常无权限执行，回退为空。</p>
     *
     * @return 主板序列号；采集失败时返回空字符串
     */
    private static String collectBoardSerial() {
        try {
            return switch (OsFamily.detect()) {
                case LINUX -> collectBoardSerialLinux();
                case MAC -> collectBoardSerialMac();
                case WINDOWS -> collectBoardSerialWindows();
                case UNKNOWN -> "";
            };
        } catch (Exception e) {
            log.debug("主板序列号采集失败（容器内通常不可用）: {}", e.getMessage());
        }
        return "";
    }

    /**
     * Linux 下通过 dmidecode 采集主板序列号。
     */
    private static String collectBoardSerialLinux() throws IOException, InterruptedException {
        String result = execCommand("dmidecode", "-s", "baseboard-serial-number");
        return result == null ? "" : result.trim();
    }

    /**
     * macOS 下通过 system_profiler 提取主板序列号。
     */
    private static String collectBoardSerialMac() throws IOException, InterruptedException {
        String output = execCommand("system_profiler", "SPHardwareDataType");
        if (output != null) {
            for (String line : output.split(LINE_SEPARATOR)) {
                if (line.contains("Serial Number")) {
                    String value = line.substring(line.indexOf(':') + 1).trim();
                    return value.isEmpty() ? "" : value;
                }
            }
        }
        return "";
    }

    /**
     * Windows 下通过 wmic 提取主板序列号。
     */
    private static String collectBoardSerialWindows() throws IOException, InterruptedException {
        String output = execCommand("wmic", "baseboard", "get", "serialnumber");
        if (output != null) {
            for (String line : output.split(LINE_SEPARATOR)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("SerialNumber")) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    /**
     * 执行系统命令并返回标准输出。
     *
     * @param command 命令及参数
     * @return 标准输出内容；执行失败时返回 null
     */
    private static String execCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.debug("命令 {} 退出码 {}", String.join(" ", command), exitCode);
            return null;
        }
        return output.toString();
    }

    /**
     * 计算 SHA-256 哈希的十六进制小写表示。
     *
     * @param input 原始字符串
     * @return 64 字符十六进制串；输入为 null 时返回空串
     */
    private static String sha256Hex(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("SHA-256 哈希计算失败", e);
            return "";
        }
    }
}

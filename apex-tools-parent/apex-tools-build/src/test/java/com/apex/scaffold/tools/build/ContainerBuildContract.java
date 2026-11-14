package com.apex.scaffold.tools.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 校验仓库现有的显式 COPY / 放行规则，不模拟容器引擎或完整 ignore 匹配语义。 */
final class ContainerBuildContract {

    private ContainerBuildContract() {}

    static void assertPomCopies(MavenModuleInventory.Inventory inventory, String containerfile) {
        Set<String> expected = new LinkedHashSet<>();
        expected.add("pom.xml");
        Path root = inventory.root().directory();
        inventory
                .modules()
                .values()
                .forEach(module -> expected.add(root.relativize(module.pom()).toString()));
        Set<String> copies = new LinkedHashSet<>();
        boolean offline = false;
        for (String line : instructions(containerfile)) {
            if (line.startsWith("RUN ") && line.contains("dependency:go-offline")) {
                offline = true;
                break;
            }
            if (line.matches("(?i)^COPY\\s+\\.\\s+\\.\\s*$")) {
                throw new AssertionError("Containerfile 完整源码 COPY 不得先于依赖预下载");
            }
            Matcher copy =
                    Pattern.compile("^COPY\\s+(\\S*pom\\.xml)\\s+(\\S+)\\s*$").matcher(line);
            if (copy.matches()) {
                String source = copy.group(1);
                assertTrue(copies.add(source), "Containerfile 重复前置 POM COPY: " + source);
                Path destination = Path.of(copy.group(2)).normalize();
                Path actual = copy.group(2).endsWith("pom.xml")
                        ? destination
                        : destination.resolve("pom.xml").normalize();
                assertEquals(Path.of(source), actual, "Containerfile POM COPY 目标位置错误: " + line);
            }
        }
        assertTrue(offline, "Containerfile 未找到依赖预下载 RUN 边界");
        assertEquals(expected, copies, "Containerfile 依赖预下载前的 POM COPY 与递归 modules 不一致");
    }

    /** 第二阶段嵌套布局：moduleDirectories 为仓库根相对的模块目录（如 apex-services-bom/apex-gateway）。 */
    static void assertPrebuiltAllowlist(Set<String> moduleDirectories, String ignore) {
        List<String> rules = instructions(ignore);
        int excluded = rules.lastIndexOf("**/target");
        assertTrue(excluded >= 0, ".containerignore 必须保留默认 target 排除");
        assertTrue(rules.contains("**/classes"), ".containerignore 必须继续排除 classes");
        assertTrue(!moduleDirectories.isEmpty(), "标准服务集合不得为空");
        for (String directory : moduleDirectories) {
            String id = Path.of(directory).getFileName().toString();
            String allowDirectory = "!" + directory + "/target/";
            String jar = "!" + directory + "/target/" + id + "-*.jar";
            int directoryIndex = rules.lastIndexOf(allowDirectory);
            int jarIndex = rules.lastIndexOf(jar);
            assertTrue(directoryIndex > excluded, id + " prebuilt 缺少默认排除后的 target 目录放行");
            assertTrue(jarIndex > directoryIndex, id + " prebuilt 缺少目录放行后的目标 JAR 放行");
            for (String reexcluded :
                    List.of(directory + "/target", directory + "/target/", directory + "/target/" + id + "-*.jar")) {
                assertTrue(rules.lastIndexOf(reexcluded) < directoryIndex, id + " prebuilt 放行后又被显式排除");
            }
        }
    }

    private static List<String> instructions(String text) {
        return text.replace("\\\r\n", " ")
                .replace("\\\n", " ")
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }
}

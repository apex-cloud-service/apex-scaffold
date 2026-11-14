package com.apex.scaffold.tools.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** 仅沿顶层 modules 收集聚合树；parent 坐标用于校验继承，不作为递归边。 */
final class MavenModuleInventory {

    private MavenModuleInventory() {}

    record Gav(String groupId, String artifactId, String version) {}

    record Module(Path pom, Gav gav, Document document, String aggregator) {
        String artifactId() {
            return gav.artifactId();
        }

        Path directory() {
            return pom.getParent();
        }
    }

    record Inventory(Module root, Map<String, Module> modules) {}

    static Inventory read(Path directory) throws Exception {
        Path root = directory.toRealPath();
        Path rootPom = checkedPom(root, root.resolve("pom.xml"));
        Module project = readModule(rootPom, null);
        Map<String, Module> modules = new LinkedHashMap<>();
        Map<Path, String> visited = new LinkedHashMap<>();
        Map<Gav, Path> coordinates = new LinkedHashMap<>();
        Set<Path> active = new LinkedHashSet<>();
        visited.put(rootPom, project.artifactId());
        coordinates.put(project.gav(), rootPom);
        active.add(rootPom);
        collect(root, project, modules, visited, coordinates, active);
        return new Inventory(project, modules);
    }

    private static void collect(
            Path root,
            Module project,
            Map<String, Module> modules,
            Map<Path, String> visited,
            Map<Gav, Path> coordinates,
            Set<Path> active)
            throws Exception {
        Element declarations = child(project.document().getDocumentElement(), "modules");
        for (Element declaration : children(declarations, "module")) {
            String relative = declaration.getTextContent().trim();
            assertFalse(relative.isBlank(), project.pom() + " 存在空 module 路径");
            Path target = project.directory().resolve(relative);
            Path pom = checkedPom(root, target.endsWith("pom.xml") ? target : target.resolve("pom.xml"));
            assertFalse(active.contains(pom), "聚合环: " + active + " -> " + pom);
            assertFalse(visited.containsKey(pom), "重复模块路径: " + pom + "，再次来自 " + project.pom());
            Module module = readModule(pom, project.artifactId());
            assertNull(coordinates.putIfAbsent(module.gav(), pom), "重复模块 GAV: " + module.gav() + "，路径 " + pom);
            Path expectedDirectory = project.aggregator() == null
                    ? root.resolve(module.artifactId())
                    : root.resolve(project.artifactId()).resolve(module.artifactId());
            assertEquals(expectedDirectory, module.directory(), "artifactId 必须匹配分组父目录: " + pom);
            assertNull(modules.putIfAbsent(module.artifactId(), module), "重复 artifactId: " + module.artifactId());
            visited.put(pom, project.artifactId());
            active.add(pom);
            collect(root, module, modules, visited, coordinates, active);
            active.remove(pom);
        }
    }

    private static Path checkedPom(Path root, Path pom) throws Exception {
        assertTrue(pom.normalize().startsWith(root), "模块路径越界: " + pom);
        assertTrue(Files.isRegularFile(pom), "缺失模块 POM: " + pom);
        Path real = pom.toRealPath();
        assertTrue(real.startsWith(root), "模块真实路径越界: " + pom + " -> " + real);
        return real;
    }

    /** 当前坐标为显式值，缺省 groupId/version 取 parent 声明；不模拟 Maven 插值或有效模型。 */
    private static Module readModule(Path pom, String aggregator) throws Exception {
        Document document = parseXml(pom);
        Element project = document.getDocumentElement();
        Element parent = child(project, "parent");
        String group = childText(project, "groupId");
        String version = childText(project, "version");
        Gav gav = new Gav(
                group.isBlank() ? childText(parent, "groupId") : group,
                childText(project, "artifactId"),
                version.isBlank() ? childText(parent, "version") : version);
        for (String value : List.of(gav.groupId(), gav.artifactId(), gav.version())) {
            assertTrue(!value.isBlank() && !value.contains("${"), "GAV 缺省或含未解析属性: " + pom + "，" + gav);
        }
        return new Module(pom, gav, document, aggregator);
    }

    static void assertManifestMatches(Inventory inventory, Set<String> registered) {
        Set<String> missing = new LinkedHashSet<>(inventory.modules().keySet());
        missing.removeAll(registered);
        Set<String> extra = new LinkedHashSet<>(registered);
        extra.removeAll(inventory.modules().keySet());
        assertEquals(inventory.modules().keySet(), registered, "递归 modules 与注册表不一致，缺项=" + missing + "，多项=" + extra);
    }

    static void assertParent(Module module, Module expected) throws Exception {
        assertNotNull(expected, module.artifactId() + " 预期 parent 未在聚合清单中登记");
        Element parent = child(module.document().getDocumentElement(), "parent");
        assertNotNull(parent, module.artifactId() + " 缺少直接 parent");
        Gav declared =
                new Gav(childText(parent, "groupId"), childText(parent, "artifactId"), childText(parent, "version"));
        assertEquals(expected.gav(), declared, module.artifactId() + " 的直接 parent GAV 不一致");
        Element relativePath = child(parent, "relativePath");
        String relative = relativePath == null
                ? "../pom.xml"
                : relativePath.getTextContent().trim();
        assertFalse(relative.isBlank(), module.artifactId() + " 的 parent relativePath 不允许禁用本地解析");
        Path target = module.directory().resolve(relative);
        assertTrue(Files.isRegularFile(target), module.artifactId() + " 的 parent POM 缺失: " + target);
        assertEquals(expected.pom(), target.toRealPath(), module.artifactId() + " 的 parent relativePath 指向错误");
    }

    static void assertGroupingParent(Module module, Module root, Map<String, String> fields) throws Exception {
        String id = module.artifactId();
        Element project = module.document().getDocumentElement();
        assertEquals("pom", childText(project, "packaging"), id + " 分组父模块必须 packaging=pom");
        assertParent(module, root);
        assertNotNull(fields, id + " 分组父模块未登记");
        assertEquals("none", fields.get("deliverable"), id + " 分组父模块不得成为可执行交付物");
        for (String field : List.of("port", "managementPort", "hostPort", "mainClass")) {
            assertNull(fields.get(field), id + " 分组父模块不得登记 " + field);
        }
        List<Element> owners = new ArrayList<>();
        owners.add(project);
        owners.addAll(children(child(project, "profiles"), "profile"));
        for (Element owner : owners) {
            for (Element dependency : children(child(owner, "dependencies"), "dependency")) {
                assertEquals("test", childText(dependency, "scope"), id + " 分组父模块不得引入直接运行依赖");
            }
        }
        for (String resource : List.of("resources", "testResources", "sourceDirectory", "testSourceDirectory")) {
            assertEquals(0, module.document().getElementsByTagName(resource).getLength(), id + " 分组父模块不得声明资源或源码目录");
        }
        for (String source : List.of("src/main/java", "src/main/resources")) {
            assertFalse(Files.exists(module.directory().resolve(source)), id + " 分组父模块不得承载 " + source);
        }
        assertEquals(0, module.document().getElementsByTagName("mainClass").getLength(), id + " 分组父模块不得声明 mainClass");
        assertFalse(hasPlugin(module.document(), "spring-boot-maven-plugin"), id + " 分组父模块不得声明 Boot 打包插件");
        assertFalse(hasPlugin(module.document(), "maven-shade-plugin"), id + " 分组父模块不得声明 Shade 打包插件");
        for (Element goal : elements(module.document(), "goal")) {
            assertFalse(
                    Set.of("repackage", "shade").contains(goal.getTextContent().trim()),
                    id + " 分组父模块不得执行 repackage/shade");
        }
    }

    static Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    static boolean hasPlugin(Document pom, String artifactId) {
        return elements(pom, "plugin").stream().anyMatch(plugin -> artifactId.equals(childText(plugin, "artifactId")));
    }

    static List<Element> elements(Document document, String name) {
        List<Element> result = new ArrayList<>();
        var nodes = document.getElementsByTagNameNS("*", name);
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    static Element child(Element parent, String name) {
        List<Element> matches = children(parent, name);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    static String childText(Element parent, String name) {
        Element element = child(parent, name);
        return element == null ? "" : element.getTextContent().trim();
    }

    static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent != null) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element && name.equals(element.getLocalName())) {
                    result.add(element);
                }
            }
        }
        return result;
    }
}

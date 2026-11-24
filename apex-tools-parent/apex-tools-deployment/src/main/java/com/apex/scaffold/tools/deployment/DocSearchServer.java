package com.apex.scaffold.tools.deployment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文档搜索静态文件服务器，替代原 {@code start-doc-search.sh} 依赖的
 * {@code python3 -m http.server}。
 *
 * <p>以项目根为文档根提供 GET/HEAD 静态文件服务：目录优先返回 index.html，
 * 无索引页时生成简易目录清单；路径中的 {@code .} 与 {@code ..} 分量被忽略。
 * Markdown 文件默认经 {@link MarkdownPageRenderer} 渲染为带 GitHub 风格标题 id 的
 * HTML 页面（文档内目录锚点可直接点击跳转），请求追加 {@code ?raw} 查询参数时
 * 仍按 {@code text/plain} 返回源码。默认仅监听回环地址，不向局域网暴露本地文件；
 * 确需局域网访问时由调用方显式传入绑定地址（如 {@code 0.0.0.0}）。仅用于本地文档浏览，
 * 不参与服务运行期装配。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class DocSearchServer {

    /** 默认绑定地址：仅本机可访问，避免把项目文件暴露给局域网。 */
    private static final String LOOPBACK = "127.0.0.1";

    private static final int MIN_ARGUMENT_COUNT = 2;
    private static final int MAX_ARGUMENT_COUNT = 3;
    private static final int EXIT_USAGE = 2;
    private static final String PATH_SEPARATOR = "/";
    private static final String HEAD_METHOD = "HEAD";
    private static final String QUERY_SEPARATOR = "&";

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("xml", "text/xml; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("md", "text/plain; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("yaml", "text/yaml; charset=utf-8"),
            Map.entry("yml", "text/yaml; charset=utf-8"),
            Map.entry("map", "application/json; charset=utf-8"));

    private DocSearchServer() {}

    /**
     * 命令行入口：参数为端口、文档根目录与可选绑定主机；服务器启动后由其后台线程持续服务。
     *
     * @param args 端口、文档根目录、绑定主机（缺省回环地址）
     * @throws IOException 端口绑定或 IO 失败
     */
    public static void main(String[] args) throws IOException {
        if (args.length != MIN_ARGUMENT_COUNT && args.length != MAX_ARGUMENT_COUNT) {
            System.err.println("usage: DocSearchServer <port> <doc-root> [bind-host]");
            System.exit(EXIT_USAGE);
        }
        String host = args.length == 3 && !args[2].isBlank() ? args[2] : LOOPBACK;
        start(Integer.parseInt(args[0]), Path.of(args[1]).toAbsolutePath().normalize(), host);
    }

    /**
     * 在回环地址启动静态文件服务并返回服务器实例，供测试复用。
     *
     * @param port 监听端口，0 表示由系统分配
     * @param root 文档根目录
     * @return 已启动的 HTTP 服务器
     * @throws IOException 端口绑定失败
     */
    public static HttpServer start(int port, Path root) throws IOException {
        return start(port, root, LOOPBACK);
    }

    /**
     * 在指定地址启动静态文件服务并返回服务器实例，供测试复用。
     *
     * @param port 监听端口，0 表示由系统分配
     * @param root 文档根目录
     * @param host 绑定地址，{@code 0.0.0.0} 表示对局域网开放
     * @return 已启动的 HTTP 服务器
     * @throws IOException 端口绑定失败
     */
    public static HttpServer start(int port, Path root, String host) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", exchange -> handle(exchange, root));
        server.start();
        System.out.println("Serving " + root + " at http://localhost:"
                + server.getAddress().getPort() + "/ (bound to " + host + ")");
        return server;
    }

    private static void handle(HttpExchange exchange, Path root) throws IOException {
        try {
            String request = exchange.getRequestURI().getPath();
            Path target = translate(root, request);
            if (target == null) {
                sendError(exchange, 404, "File not found");
                return;
            }
            if (Files.isDirectory(target)) {
                Path index = target.resolve("index.html");
                if (Files.isRegularFile(index)) {
                    sendFile(exchange, index);
                } else {
                    sendListing(exchange, target, root);
                }
                return;
            }
            if (Files.isRegularFile(target)) {
                if (isMarkdownFile(target) && !wantsRawSource(exchange)) {
                    sendRenderedMarkdown(exchange, target);
                } else {
                    sendFile(exchange, target);
                }
                return;
            }
            sendError(exchange, 404, "File not found");
        } finally {
            exchange.close();
        }
    }

    /** 解码请求路径并忽略 {@code .}、{@code ..} 分量，阻止越出文档根。 */
    private static Path translate(Path root, String request) {
        String decoded = URLDecoder.decode(request, StandardCharsets.UTF_8);
        Path current = root;
        for (String part : decoded.split(PATH_SEPARATOR)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                continue;
            }
            current = current.resolve(part);
        }
        if (!current.startsWith(root)) {
            return null;
        }
        return current;
    }

    private static void sendFile(HttpExchange exchange, Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String contentType = dot > 0
                ? CONTENT_TYPES.getOrDefault(
                        name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT), "application/octet-stream")
                : "application/octet-stream";
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (HEAD_METHOD.equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** 判断文件是否为 Markdown 文档（大小写不敏感）。 */
    private static boolean isMarkdownFile(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && name.substring(dot + 1).equalsIgnoreCase("md");
    }

    /** 请求带 {@code ?raw}（可带任意值）查询参数时返回 Markdown 源码而非渲染页面。 */
    private static boolean wantsRawSource(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return false;
        }
        for (String part : query.split(QUERY_SEPARATOR)) {
            String name = part.indexOf('=') >= 0 ? part.substring(0, part.indexOf('=')) : part;
            if ("raw".equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** 将 Markdown 渲染为 HTML 页面返回；HEAD 请求仅返回响应头。 */
    private static void sendRenderedMarkdown(HttpExchange exchange, Path file) throws IOException {
        String markdown = Files.readString(file, StandardCharsets.UTF_8);
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String page = MarkdownPageRenderer.render(markdown, dot > 0 ? name.substring(0, dot) : name);
        byte[] body = page.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        if (HEAD_METHOD.equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void sendListing(HttpExchange exchange, Path dir, Path root) throws IOException {
        String relative = root.relativize(dir).toString().replace('\\', '/');
        String display = relative.isEmpty() ? "/" : "/" + relative + "/";
        String title = "Directory listing for " + display;
        String items;
        try (Stream<Path> entries = Files.list(dir)) {
            items = entries.sorted((a, b) -> a.getFileName()
                            .toString()
                            .compareToIgnoreCase(b.getFileName().toString()))
                    .map(entry -> {
                        String name = entry.getFileName().toString();
                        String suffix = Files.isDirectory(entry) ? "/" : "";
                        return "<li><a href=\"" + display + name + suffix + "\">" + name + suffix + "</a></li>";
                    })
                    .collect(Collectors.joining("\n"));
        }
        byte[] body = ("<!DOCTYPE HTML>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>"
                        + title + "</title>\n</head>\n<body>\n<h1>" + title + "</h1>\n<ul>\n"
                        + items + "\n</ul>\n</body>\n</html>\n")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = ("<html><head><title>Error response</title></head><body><h1>Error response" + "</h1><p>" + message
                        + "</p></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}

package com.apex.scaffold.tools.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于 mysql 命令行的客户端实现。
 *
 * <p>服务端错误不回显，防止把应用数据写进运维日志；子进程有硬性超时，避免无人值守挂起。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class MysqlClient implements RecoverClient {

    /** 单条语句最长执行时间。 */
    private static final long TIMEOUT_SECONDS = 90;

    /** 输出读取线程的回收等待时间。 */
    private static final long DRAIN_JOIN_MILLIS = 5000;

    /** 统一使用 utf8mb4，避免中文数据被客户端截断。 */
    private static final List<String> CLIENT_OPTIONS =
            List.of("--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> command;

    private final String database;

    /**
     * 构造客户端。
     *
     * @param command  mysql 客户端命令前缀
     * @param database 目标数据库
     */
    public MysqlClient(List<String> command, String database) {
        this.command = command;
        this.database = database;
    }

    @Override
    public String database() {
        return database;
    }

    @Override
    public String execute(String sql) throws Exception {
        List<String> full = new ArrayList<>(command);
        full.addAll(CLIENT_OPTIONS);
        full.add(database);
        Process process = new ProcessBuilder(full).start();
        write(process, sql);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> outDrain = pool.submit(() -> drain(process.getInputStream(), stdout));
            Future<?> errDrain = pool.submit(() -> drain(process.getErrorStream(), OutputStream.nullOutputStream()));
            waitFor(process);
            outDrain.get(DRAIN_JOIN_MILLIS, TimeUnit.MILLISECONDS);
            errDrain.get(DRAIN_JOIN_MILLIS, TimeUnit.MILLISECONDS);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("mysql command failed; inspect server locally (output withheld)");
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    @Override
    public List<List<Object>> rows(String query) throws Exception {
        String text = execute(query);
        List<List<Object>> rows = new ArrayList<>();
        if (text.isEmpty()) {
            return rows;
        }
        for (String line : text.split("\n")) {
            rows.add(JSON.readValue(line, List.class));
        }
        return rows;
    }

    /**
     * 将 SQL 写入子进程标准输入；子进程提前退出时忽略 Broken Pipe。
     *
     * @param process 子进程
     * @param sql     待执行 SQL
     */
    private static void write(Process process, String sql) {
        try (OutputStream input = process.getOutputStream()) {
            input.write(sql.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 与 mysql CLI 非交互行为一致：客户端提前退出时不视为致命错误，由退出码判定结果
        }
    }

    /**
     * 等待子进程结束，超时强制销毁。
     *
     * @param process 子进程
     * @throws Exception 超时或等待被中断时抛出
     */
    private static void waitFor(Process process) throws Exception {
        if (process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return;
        }
        process.destroyForcibly();
        throw new TimeoutException("mysql command timed out after " + TIMEOUT_SECONDS + " seconds");
    }

    /**
     * 读完并丢弃指定流，避免子进程因管道写满而阻塞。
     *
     * @param in  输入流
     * @param out 输出流
     */
    private static void drain(InputStream in, OutputStream out) {
        try (in) {
            in.transferTo(out);
        } catch (IOException ignored) {
            // 流在进程销毁时可能被关闭，读取失败不影响退出码判定
        }
    }
}

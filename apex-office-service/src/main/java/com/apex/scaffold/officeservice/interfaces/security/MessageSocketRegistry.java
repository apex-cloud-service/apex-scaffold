package com.apex.scaffold.officeservice.interfaces.security;

import com.apex.scaffold.office.off.application.service.MessageCommittedEvent;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 单实例站内信连接与一次性票据注册表，所有容量均有上限。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Component
public class MessageSocketRegistry {
    private static final int MAX_TICKETS = 4096;
    private static final int MAX_CONNECTIONS = 1024;
    private static final int QUEUE_CAPACITY = 64;

    /** 消息提示帧前缀，仅用于通知客户端拉取，不承载消息正文。 */
    private static final String MESSAGE_FRAME_PREFIX = "{\"type\":\"message\",\"messageId\":\"";

    /** 消息提示帧后缀。 */
    private static final String MESSAGE_FRAME_SUFFIX = "\"}";

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new HashMap<>();
    private final Map<String, Connection> connections = new HashMap<>();

    /**
     * 签发有效期三十秒的随机票据，过期票据在下一次签发时回收。
     * @param user 已验签用户
     * @return 不透明一次性票据
     */
    public synchronized String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        tickets.values().removeIf(ticket -> !ticket.expiresAt().isAfter(now));
        if (tickets.size() >= MAX_TICKETS) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(value, new Ticket(user, now.plusSeconds(30)));
        return value;
    }

    /**
     * 原子消费票据，重复、伪造或过期票据均不能认证。
     * @param value 票据
     * @return 可信身份，失败返回空
     */
    public synchronized AuthenticatedUser consume(String value) {
        Ticket ticket = value == null ? null : tickets.remove(value);
        return ticket != null && ticket.expiresAt().isAfter(Instant.now()) ? ticket.user() : null;
    }

    /**
     * 登记连接及其独立有界队列。
     * @param user 可信用户
     * @return 已登记连接
     */
    public synchronized Connection connect(AuthenticatedUser user) {
        if (connections.size() >= MAX_CONNECTIONS) {
            throw new IllegalStateException("站内信连接容量已满");
        }
        Connection connection =
                new Connection(UUID.randomUUID().toString(), user, new ArrayBlockingQueue<>(QUEUE_CAPACITY));
        connections.put(connection.id(), connection);
        return connection;
    }

    /**
     * 移除断开连接并释放队列。
     * @param connection 待移除连接
     */
    public synchronized void disconnect(Connection connection) {
        connections.remove(connection.id());
        connection.queue().clear();
    }

    /**
     * 向同租户指定用户的所有连接发送提示；慢连接队列满时标记关闭。
     * @param tenant 租户
     * @param user 用户
     * @param message 消息标识
     */
    public synchronized void publish(String tenant, Long user, Long message) {
        connections.values().stream()
                .filter(connection -> tenant.equals(connection.user().tenantId())
                        && user.equals(connection.user().userId()))
                .forEach(connection -> {
                    if (!connection.queue().offer(MESSAGE_FRAME_PREFIX + message + MESSAGE_FRAME_SUFFIX)) {
                        connection.overflow = true;
                    }
                });
    }

    /**
     * 接收已完成本地事务的消息事件，失败不改变已提交的业务结果。
     * @param event 已提交消息
     */
    @EventListener
    public void onCommitted(MessageCommittedEvent event) {
        event.recipientIds().forEach(user -> publish(event.tenantId(), user, event.messageId()));
    }

    /**
     * 返回当前连接数供容量验证使用。
     * @return 连接数
     */
    public synchronized int connectionCount() {
        return connections.size();
    }

    /** 停机时清理所有凭据与队列。 */
    @PreDestroy
    public synchronized void close() {
        tickets.clear();
        connections.values().forEach(connection -> connection.overflow = true);
        connections.clear();
    }

    /**
     * 一次性短期身份票据。
     * @author Apex Team
     * @version 1.3.1
     * @since 1.3.1
     */
    private record Ticket(AuthenticatedUser user, Instant expiresAt) {}

    /**
     * 单个连接的身份与发送队列。
     * @author Apex Team
     * @version 1.3.1
     * @since 1.3.1
     */
    public static final class Connection {
        private final String id;
        private final AuthenticatedUser user;
        private final ArrayBlockingQueue<String> queue;
        private volatile boolean overflow;

        /**
         * 构造连接状态。
         * @param id 连接标识
         * @param user 可信用户
         * @param queue 有界队列
         */
        private Connection(String id, AuthenticatedUser user, ArrayBlockingQueue<String> queue) {
            this.id = id;
            this.user = user;
            this.queue = queue;
        }

        /** @return 连接标识。 */
        public String id() {
            return id;
        }
        /** @return 可信身份。 */
        public AuthenticatedUser user() {
            return user;
        }
        /** @return 有界发送队列。 */
        public ArrayBlockingQueue<String> queue() {
            return queue;
        }
        /** @return 是否发生缓冲溢出。 */
        public boolean overflow() {
            return overflow;
        }
    }
}

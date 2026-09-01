package com.apex.scaffold.infrastructure.mq.listener;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.OperationLogRepository;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.CacheService;
import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 用户消息监听器
 *
 * <p>处理用户相关的消息，包括用户注册、登录、退出和信息更新等事件。
 *
 * <h2>监听的消息类型</h2>
 * <ul>
 *   <li>{@link MqConstant#TAG_USER_REGISTER} - 用户注册消息</li>
 *   <li>{@link MqConstant#TAG_USER_LOGIN} - 用户登录消息</li>
 *   <li>{@link MqConstant#TAG_USER_LOGOUT} - 用户退出消息</li>
 *   <li>{@link MqConstant#TAG_USER_UPDATE} - 用户信息更新消息</li>
 * </ul>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>消息接收</b>：通过RocketMQ接收用户相关的消息</li>
 *   <li><b>消息路由</b>：根据消息标签分发到不同的处理方法</li>
 *   <li><b>幂等处理</b>：通过MQ消费者服务确保消息处理的幂等性</li>
 *   <li><b>异常记录</b>：记录消息处理过程中的异常信息</li>
 *   <li><b>用户后置处理</b>：注册欢迎消息、登录奖励、缓存清理等</li>
 * </ul>
 *
 * <h2>消费确认说明</h2>
 * <p>消息处理涉及的持久化和缓存操作在监听器返回前完成。监听器采用顺序消费模式，
 * 同一消息队列内按 RocketMQ 投递顺序处理；处理异常会向 RocketMQ 抛出并触发重消费，
 * 只有完整处理成功后才确认消息。</p>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see RocketMQListener
 * @see MqConsumerService
 */
@Component
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
@ConditionalOnProperty(name = "apex.mq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        consumerGroup = MqConstant.CONSUMER_GROUP_USER,
        topic = MqConstant.TOPIC_USER,
        selectorExpression = MqConstant.TAG_USER_REGISTER
                + " || "
                + MqConstant.TAG_USER_LOGIN
                + " || "
                + MqConstant.TAG_USER_LOGOUT
                + " || "
                + MqConstant.TAG_USER_UPDATE,
        consumeMode = ConsumeMode.ORDERLY,
        maxReconsumeTimes = MqConstant.MAX_RECONSUME_TIMES)
public class UserMessageListener implements RocketMQListener<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(UserMessageListener.class);

    /** MQ 消费者服务，用于处理消息并确保幂等性 */
    private final MqConsumerService mqConsumerService;

    /** 用户仓储，用于查询和更新用户信息 */
    private final UserRepository userRepository;

    /** 系统消息仓储，用于发送系统通知 */
    private final SystemMessageRepository systemMessageRepository;

    /** 操作日志仓储，用于记录安全审计日志 */
    private final OperationLogRepository operationLogRepository;

    /** 缓存服务，用于清理用户缓存 */
    private final CacheService cacheService;

    /** 消息标签到类型化处理函数的不可变注册表。 */
    private final Map<String, Consumer<UserMessagePayload>> handlers;

    /**
     * 创建用户消息监听器并注册各消息 Handler。
     *
     * <p>注册表使用方法引用实现函数式 Handler。新增消息类型只需新增处理方法并注册标签，
     * 消费主流程无需再增加 {@code switch} 分支。
     *
     * @param mqConsumerService 幂等消费服务
     * @param userRepository 用户仓储
     * @param systemMessageRepository 系统消息仓储
     * @param operationLogRepository 操作日志仓储
     * @param cacheService 缓存服务
     */
    public UserMessageListener(
            MqConsumerService mqConsumerService,
            UserRepository userRepository,
            SystemMessageRepository systemMessageRepository,
            OperationLogRepository operationLogRepository,
            CacheService cacheService) {
        this.mqConsumerService = mqConsumerService;
        this.userRepository = userRepository;
        this.systemMessageRepository = systemMessageRepository;
        this.operationLogRepository = operationLogRepository;
        this.cacheService = cacheService;
        Map<String, Consumer<UserMessagePayload>> registry = new LinkedHashMap<>();
        register(registry, MqConstant.TAG_USER_REGISTER, this::handleUserRegister);
        register(registry, MqConstant.TAG_USER_LOGIN, this::handleUserLogin);
        register(registry, MqConstant.TAG_USER_LOGOUT, this::handleUserLogout);
        register(registry, MqConstant.TAG_USER_UPDATE, this::handleUserUpdate);
        this.handlers = Map.copyOf(registry);
    }

    /**
     * 注册消息处理函数并拒绝重复标签。
     *
     * <p>重复标签通常意味着两个 Handler 竞争同一类消息。监听器在启动构造阶段立即失败，
     * 避免后注册的 Lambda 静默覆盖前一个处理流程。
     *
     * @param registry 正在构建的 Handler 注册表
     * @param tag RocketMQ 消息标签
     * @param handler 标签对应的类型化处理函数
     * @throws IllegalStateException 标签已注册时抛出
     */
    private void register(
            Map<String, Consumer<UserMessagePayload>> registry, String tag, Consumer<UserMessagePayload> handler) {
        if (registry.putIfAbsent(tag, handler) != null) {
            throw new IllegalStateException("用户消息标签重复注册: " + tag);
        }
    }

    /**
     * 接收并处理用户相关的 MQ 消息。
     *
     * @param message 消息体
     */
    @Override
    /**
     * 消费消息
     *
     * @param message 参数
     */
    public void onMessage(Map<String, Object> message) {
        UserMessagePayload payload = UserMessagePayload.from(message);
        String messageId = payload.messageId();
        String tag = payload.tag();

        log.info("接收到用户消息, messageId: {}, tag: {}", messageId, tag);

        String previousTenantId = TenantContext.currentTenantId().orElse(null);
        TenantContext.setTenantId(payload.tenantId());
        try {
            boolean processed = mqConsumerService.processMessage(messageId, message, ignored -> {
                if (tag == null) {
                    throw new IllegalArgumentException("消息缺少tag字段");
                }
                Consumer<UserMessagePayload> handler = handlers.get(tag);
                if (handler == null) {
                    throw new IllegalArgumentException("未知的用户消息标签: " + tag);
                }
                handler.accept(payload);
                return true;
            });
            if (!processed) {
                throw new IllegalStateException("用户消息消费失败, messageId: " + messageId);
            }
        } finally {
            if (previousTenantId == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenantId);
            }
        }
    }

    /**
     * 处理用户注册消息
     *
     * <p>发送欢迎系统消息，并记录注册操作日志。
     *
     * @param payload 类型化消息，应包含 userId、nickName 等字段
     */
    private void handleUserRegister(UserMessagePayload payload) {
        log.info("处理用户注册消息: {}", payload.raw());
        Long userId = payload.userId();
        String nickName = payload.nickName();

        if (userId != null) {
            // 1. 发送注册欢迎系统消息
            SystemMessageEntity welcomeMsg = new SystemMessageEntity();
            welcomeMsg.setUserId(userId);
            welcomeMsg.setMsgType("WELCOME");
            welcomeMsg.setContent("欢迎加入 Apex！您的账号已注册成功，开始探索你的审美偏好之旅吧。");
            welcomeMsg.setCreateTime(LocalDateTime.now());
            welcomeMsg.setUpdateTime(LocalDateTime.now());
            systemMessageRepository.save(welcomeMsg).block();

            // 2. 记录注册操作日志
            OperationLogEntity logEntity = new OperationLogEntity();
            logEntity.setUserId(userId);
            logEntity.setModule("USER");
            logEntity.setOperationType("USER_REGISTER");
            logEntity.setDescription("用户注册: " + nickName);
            logEntity.setIp(payload.clientIp());
            logEntity.setOperationTime(LocalDateTime.now());
            operationLogRepository.insert(logEntity).block();
        }

        log.info("用户注册后置处理完成, userId: {}, nickName: {}", userId, nickName);
    }

    /**
     * 处理用户登录消息
     *
     * <p>更新用户最后登录时间、发放每日登录积分奖励、记录登录操作日志。
     *
     * @param payload 类型化消息，应包含 userId、clientIp、deviceId 等字段
     */
    private void handleUserLogin(UserMessagePayload payload) {
        log.info("处理用户登录消息: {}", payload.raw());
        Long userId = payload.userId();

        if (userId == null) {
            log.warn("登录消息缺少 userId，跳过处理");
            return;
        }

        // 1. 更新用户最后登录时间
        userRepository
                .findById(userId)
                .flatMap(user -> {
                    user.setLastLoginTime(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .block();

        // 2. 每日登录奖励由首次有效投票触发（§8「每日登录并完成投票行为+3」），
        //    不再在登录时自动发放，避免登录即领取而不需投票。

        // 3. 记录登录日志（设备信息、IP）
        String clientIp = payload.clientIp() == null ? "unknown" : payload.clientIp();
        String deviceId = payload.deviceId();
        OperationLogEntity logEntity = new OperationLogEntity();
        logEntity.setUserId(userId);
        logEntity.setModule("USER");
        logEntity.setOperationType("USER_LOGIN");
        logEntity.setDescription("用户登录, deviceId: " + deviceId + ", ip: " + clientIp);
        logEntity.setIp(clientIp);
        logEntity.setDeviceType(deviceId);
        logEntity.setOperationTime(LocalDateTime.now());
        operationLogRepository.insert(logEntity).block();

        log.info("用户登录后置处理完成, userId: {}, ip: {}, deviceId: {}", userId, clientIp, deviceId);
    }

    /**
     * 处理用户退出消息
     *
     * <p>清理用户临时缓存和会话数据，记录退出日志。
     *
     * @param payload 类型化消息，应包含 userId 等字段
     */
    private void handleUserLogout(UserMessagePayload payload) {
        log.info("处理用户退出消息: {}", payload.raw());
        Long userId = payload.userId();

        if (userId == null) {
            log.warn("退出消息缺少 userId，跳过处理");
            return;
        }

        // 1. 清理用户临时缓存
        cacheService.delete("user:session:" + userId, "user:token:" + userId).block();

        // 2. 记录退出日志便于安全审计
        OperationLogEntity logEntity = new OperationLogEntity();
        logEntity.setUserId(userId);
        logEntity.setModule("USER");
        logEntity.setOperationType("USER_LOGOUT");
        logEntity.setDescription("用户退出登录");
        logEntity.setIp(payload.clientIp());
        logEntity.setOperationTime(LocalDateTime.now());
        operationLogRepository.insert(logEntity).block();

        log.info("用户退出后置处理完成, userId: {}", userId);
    }

    /**
     * 处理用户信息更新消息
     *
     * <p>清理相关缓存以保证数据一致性，记录更新操作日志。
     *
     * @param payload 类型化消息，应包含 userId、updatedFields 等字段
     */
    private void handleUserUpdate(UserMessagePayload payload) {
        log.info("处理用户信息更新消息: {}", payload.raw());
        Long userId = payload.userId();

        if (userId == null) {
            log.warn("更新消息缺少 userId，跳过处理");
            return;
        }

        // 1. 清理用户相关缓存
        cacheService.delete("user:id:" + userId, "user:openId:*").block();

        // 2. 记录更新操作日志
        String updatedFields = payload.updatedFields();
        OperationLogEntity logEntity = new OperationLogEntity();
        logEntity.setUserId(userId);
        logEntity.setModule("USER");
        logEntity.setOperationType("USER_UPDATE");
        logEntity.setDescription("用户信息更新: " + updatedFields);
        logEntity.setIp(payload.clientIp());
        logEntity.setOperationTime(LocalDateTime.now());
        operationLogRepository.insert(logEntity).block();

        log.info("用户信息更新后置处理完成, userId: {}, updatedFields: {}", userId, updatedFields);
    }
}

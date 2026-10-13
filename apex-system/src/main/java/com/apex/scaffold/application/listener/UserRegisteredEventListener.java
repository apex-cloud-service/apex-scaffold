package com.apex.scaffold.application.listener;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.event.UserRegisteredEvent;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import com.apex.scaffold.domain.repository.UserProfileRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 用户注册事件监听器
 *
 * <p>处理用户注册成功后的后续操作（欢迎系统消息 + 默认用户档案初始化）。
 * 与 C2 并发修复保持一致：监听器内<b>绝不调用 .block() 阻塞发布线程</b>，而是将所有
 * Repository 操作组合为 Mono 链、在专用阻塞池 event() 上受控提交；并在执行线程
 * 内安装/恢复事件 DTO 内携带的 {@code tenantId}（而不是依赖发布侧 ThreadLocal，
 * 因为 publishAsync 会切线程，原 ThreadLocal 已失效），最后仅 fire-once subscribe。
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserRegisteredEventListener {

    private final SystemMessageRepository systemMessageRepository;

    private final UserProfileRepository userProfileRepository;

    /**
     * 构造器注入依赖
     *
     * <p>所有依赖均使用 {@link Optional} 接收，确保任何依赖未注册时服务仍可正常启动。</p>
     *
     * @param systemMessageRepository 系统消息仓储（可选）
     * @param userProfileRepository   用户档案仓储（可选）
     */
    public UserRegisteredEventListener(
            Optional<SystemMessageRepository> systemMessageRepository,
            Optional<UserProfileRepository> userProfileRepository) {
        this.systemMessageRepository = systemMessageRepository.orElse(null);
        this.userProfileRepository = userProfileRepository.orElse(null);
    }

    /**
     * 处理用户注册事件
     *
     * <p>当用户注册成功时触发，发送欢迎系统消息并初始化默认用户档案。</p>
     *
     * @param event 用户注册事件
     */
    @EventListener(UserRegisteredEvent.class)
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        if (event == null) {
            log.warn("忽略空的用户注册事件");
            return;
        }
        log.info(
                "处理用户注册事件: userId={}, nickName={}, phone={}", event.getUserId(), event.getNickName(), event.getPhone());

        buildPipeline(event)
                .subscribeOn(BlockingIoSchedulers.event())
                .doOnSuccess(v -> log.info("用户注册事件处理完成: userId={}", event.getUserId()))
                .doOnError(e -> log.error("handleUserRegisteredEvent 异常: userId={}", event.getUserId(), e))
                .subscribe();
    }

    /**
     * 构造用户注册事件处理管道（无 .block()，纯 Mono 组合）。
     *
     * <ul>
     *   <li>在订阅时安装事件 DTO 内携带的 tenantId，完成后 finally 恢复（线程池复用安全）</li>
     *   <li>并行执行：欢迎消息 save()、用户档案幂等初始化（findByUserId → 不存在才 save）</li>
     * </ul>
     *
     * @param event 用户注册事件
     * @return 组合后的 Mono 管道（无 subscribe）
     */
    private Mono<Void> buildPipeline(UserRegisteredEvent event) {
        // usingWhen 的 resource 不能为 null（否则跳过 closure），
        // 使用 1 元素数组作为 previous 的非空 holder 保证正常执行 cleanup。
        String tenantId = event.getTenantId();
        String[] holder = new String[1];
        return Mono.usingWhen(
                        Mono.fromCallable(() -> {
                            holder[0] = TenantContext.setTenantId(tenantId);
                            return holder;
                        }),
                        ignored -> Mono.zipDelayError(
                                        sendWelcomeMessage(event).onErrorResume(e -> {
                                            log.error("注册欢迎消息发送失败: userId={}", event.getUserId(), e);
                                            return Mono.empty();
                                        }),
                                        initDefaultProfileIfAbsent(event).onErrorResume(e -> {
                                            log.error("默认用户档案初始化失败: userId={}", event.getUserId(), e);
                                            return Mono.empty();
                                        }))
                                .then(),
                        ignored -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])),
                        (ignored, err) -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])),
                        ignored -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private Mono<Void> sendWelcomeMessage(UserRegisteredEvent event) {
        if (systemMessageRepository == null) {
            log.warn("SystemMessageRepository 未注入，跳过欢迎消息发送: userId={}", event.getUserId());
            return Mono.empty();
        }
        SystemMessageEntity welcomeMsg = new SystemMessageEntity();
        welcomeMsg.setUserId(event.getUserId());
        welcomeMsg.setMsgType("WELCOME");
        welcomeMsg.setContent("欢迎加入 Apex！" + (event.getNickName() != null ? event.getNickName() : "")
                + "，开始探索你的审美偏好之旅吧。上传你的第一张照片即可开始！");
        welcomeMsg.setRefType("USER");
        welcomeMsg.setRefId(event.getUserId());
        welcomeMsg.setCreateTime(LocalDateTime.now());
        welcomeMsg.setUpdateTime(LocalDateTime.now());
        return systemMessageRepository
                .save(welcomeMsg)
                .doOnSuccess(v -> log.info("注册欢迎消息发送成功: userId={}", event.getUserId()))
                .then();
    }

    private Mono<Void> initDefaultProfileIfAbsent(UserRegisteredEvent event) {
        if (userProfileRepository == null) {
            log.warn("UserProfileRepository 未注入，跳过默认档案初始化: userId={}", event.getUserId());
            return Mono.empty();
        }
        return userProfileRepository
                .findByUserId(event.getUserId())
                .map(p -> true)
                .defaultIfEmpty(false)
                .flatMap(alreadyExists -> {
                    if (Boolean.TRUE.equals(alreadyExists)) {
                        log.info("用户档案已存在，跳过初始化: userId={}", event.getUserId());
                        return Mono.empty();
                    }
                    UserProfileEntity profile = UserProfileEntity.create(event.getUserId());
                    return userProfileRepository
                            .save(profile)
                            .doOnSuccess(v -> log.info("默认用户档案初始化成功: userId={}", event.getUserId()))
                            .then();
                });
    }
}

package com.apex.scaffold.application.listener;

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

/**
 * 用户注册事件监听器
 *
 * <p>处理用户注册成功后的后续操作，如发送欢迎消息、初始化用户档案等
 *
 * <p>P0-13修复: 从Stub实现升级为基础实现</p>
 *
 * @author Apex Team
 * @version 2.0.0
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
     * <p>当用户注册成功时触发，发送欢迎系统消息并初始化默认用户档案。
     *
     * @param event 用户注册事件
     */
    @EventListener(UserRegisteredEvent.class)
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        if (event == null) {
            log.warn("忽略空的用户注册事件");
            return;
        }
        try {
            log.info(
                    "处理用户注册事件: userId={}, nickName={}, phone={}",
                    event.getUserId(),
                    event.getNickName(),
                    event.getPhone());

            // 1. 发送欢迎系统消息
            if (systemMessageRepository != null) {
                SystemMessageEntity welcomeMsg = new SystemMessageEntity();
                welcomeMsg.setUserId(event.getUserId());
                welcomeMsg.setMsgType("WELCOME");
                welcomeMsg.setContent("欢迎加入 Apex！" + (event.getNickName() != null ? event.getNickName() : "")
                        + "，开始探索你的审美偏好之旅吧。上传你的第一张照片即可开始！");
                welcomeMsg.setRefType("USER");
                welcomeMsg.setRefId(event.getUserId());
                welcomeMsg.setCreateTime(LocalDateTime.now());
                welcomeMsg.setUpdateTime(LocalDateTime.now());

                systemMessageRepository
                        .save(welcomeMsg)
                        .doOnSuccess(v -> log.info("注册欢迎消息发送成功: userId={}", event.getUserId()))
                        .doOnError(e -> log.error("注册欢迎消息发送失败: userId={}", event.getUserId(), e))
                        .block();
            } else {
                log.warn("SystemMessageRepository 未注入，跳过欢迎消息发送: userId={}", event.getUserId());
            }

            // 2. 初始化默认用户档案（幂等：若已存在则跳过）
            if (userProfileRepository != null) {
                Boolean alreadyExists = userProfileRepository
                        .findByUserId(event.getUserId())
                        .map(p -> true)
                        .defaultIfEmpty(false)
                        .block();

                if (Boolean.TRUE.equals(alreadyExists)) {
                    log.info("用户档案已存在，跳过初始化: userId={}", event.getUserId());
                } else {
                    UserProfileEntity profile = UserProfileEntity.create(event.getUserId());
                    userProfileRepository
                            .save(profile)
                            .doOnSuccess(v -> log.info("默认用户档案初始化成功: userId={}", event.getUserId()))
                            .doOnError(e -> log.error("默认用户档案初始化失败: userId={}", event.getUserId(), e))
                            .block();
                }
            } else {
                log.warn("UserProfileRepository 未注入，跳过默认档案初始化: userId={}", event.getUserId());
            }

            log.info("用户注册事件处理完成");
        } catch (Exception e) {
            log.error("handleUserRegisteredEvent 异常: userId={}", event.getUserId(), e);
            throw new IllegalStateException("处理用户注册事件失败", e);
        }
    }
}

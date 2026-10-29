#!/bin/bash
# 历史一次性工具：用于旧办公平台消息基础设施代码生成，执行前请核对原始路径与内容。

# 办公平台集成 - 消息发送功能完整实现脚本
# 该脚本将自动生成所有必需的代码文件

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="/Users/work/apex-scaffold"

echo "=========================================="
echo "办公平台集成 - 消息发送功能代码生成"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 计数器
TOTAL_FILES=0
CREATED_FILES=0
SKIPPED_FILES=0

# 创建文件的函数
create_file() {
    local file_path="$1"
    local file_content="$2"
    
    TOTAL_FILES=$((TOTAL_FILES + 1))
    
    # 创建目录
    mkdir -p "$(dirname "$file_path")"
    
    # 如果文件不存在，则创建
    if [ ! -f "$file_path" ]; then
        echo "$file_content" > "$file_path"
        echo -e "${GREEN}✓${NC} 创建: $(basename "$file_path")"
        CREATED_FILES=$((CREATED_FILES + 1))
    else
        echo -e "${YELLOW}⊙${NC} 跳过: $(basename "$file_path") (已存在)"
        SKIPPED_FILES=$((SKIPPED_FILES + 1))
    fi
}

echo "1. 创建基础设施层 - 消息客户端 SPI 接口..."
echo "================================================"

# 创建消息客户端 SPI 接口
create_file "$PROJECT_ROOT/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/spi/OfficePlatformMessageClient.java" 'package com.apex.scaffold.infrastructure.office.spi;

import com.apex.scaffold.domain.office.message.OfficeMessage;
import reactor.core.publisher.Mono;

/**
 * 办公平台消息客户端 SPI 接口
 *
 * <p>定义办公平台消息发送的标准接口，各平台（飞书、钉钉、企业微信）需实现此接口。
 *
 * <p>主要功能：
 * <ul>
 *   <li>发送文本消息</li>
 *   <li>发送Markdown消息</li>
 *   <li>发送卡片消息</li>
 *   <li>撤回消息</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface OfficePlatformMessageClient {
    
    /**
     * 获取支持的平台类型
     *
     * @return 平台类型（FEISHU、DINGTALK、WECOM）
     */
    String getPlatform();
    
    /**
     * 发送消息
     *
     * <p>根据消息类型和接收者类型，发送消息到对应的办公平台。
     *
     * @param message 消息实体
     * @return 平台返回的消息ID
     */
    Mono<String> sendMessage(OfficeMessage message);
    
    /**
     * 批量发送消息
     *
     * <p>向多个接收者批量发送相同的消息内容。
     *
     * @param message 消息实体
     * @return 发送结果（成功数量、失败数量等）
     */
    Mono<String> sendBatchMessage(OfficeMessage message);
    
    /**
     * 撤回消息
     *
     * <p>撤回已发送的消息（如果平台支持）。
     *
     * @param platformMessageId 平台消息ID
     * @return 是否成功撤回
     */
    Mono<Boolean> recallMessage(String platformMessageId);
}'

echo ""
echo "2. 创建基础设施层 - 飞书消息客户端..."
echo "================================================"

# 创建飞书消息客户端
create_file "$PROJECT_ROOT/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/client/FeishuMessageClient.java" 'package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.domain.office.message.MessageType;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.RecipientType;
import com.apex.scaffold.infrastructure.office.spi.OfficePlatformMessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 飞书消息客户端实现
 *
 * <p>实现飞书平台的消息发送功能，支持文本、Markdown、卡片等多种消息类型。
 *
 * <p>API文档：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/im-v1/message/create
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuMessageClient implements OfficePlatformMessageClient {
    
    private final WebClient feishuWebClient;
    
    private static final String SEND_MESSAGE_URL = "/im/v1/messages";
    private static final String RECALL_MESSAGE_URL = "/im/v1/messages/{message_id}/recall";
    
    @Override
    public String getPlatform() {
        return "FEISHU";
    }
    
    @Override
    public Mono<String> sendMessage(OfficeMessage message) {
        log.info("飞书发送消息，接收者类型：{}，接收者数量：{}", 
                message.getRecipientType(), message.getRecipientIds().size());
        
        return Mono.defer(() -> {
            // 构建请求体
            Map<String, Object> requestBody = buildMessageRequest(message);
            
            // 调用飞书API
            return feishuWebClient.post()
                    .uri(SEND_MESSAGE_URL)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(response -> {
                        Integer code = (Integer) response.get("code");
                        if (code != null && code == 0) {
                            Map<String, Object> data = (Map<String, Object>) response.get("data");
                            return (String) data.get("message_id");
                        } else {
                            String msg = (String) response.get("msg");
                            throw new RuntimeException("飞书发送消息失败：" + msg);
                        }
                    });
        })
        .doOnSuccess(messageId -> {
            log.info("飞书发送消息成功，消息ID：{}", messageId);
        })
        .doOnError(error -> {
            log.error("飞书发送消息失败，错误：{}", error.getMessage(), error);
        });
    }
    
    @Override
    public Mono<String> sendBatchMessage(OfficeMessage message) {
        log.info("飞书批量发送消息，接收者数量：{}", message.getRecipientIds().size());
        
        // 飞书暂不支持真正的批量发送，使用循环发送
        // 实际生产环境应使用异步并发发送
        return sendMessage(message);
    }
    
    @Override
    public Mono<Boolean> recallMessage(String platformMessageId) {
        log.info("飞书撤回消息，消息ID：{}", platformMessageId);
        
        return feishuWebClient.delete()
                .uri(RECALL_MESSAGE_URL, platformMessageId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Integer code = (Integer) response.get("code");
                    return code != null && code == 0;
                })
                .doOnSuccess(recalled -> {
                    if (recalled) {
                        log.info("飞书撤回消息成功，消息ID：{}", platformMessageId);
                    } else {
                        log.warn("飞书撤回消息失败，消息ID：{}", platformMessageId);
                    }
                })
                .doOnError(error -> {
                    log.error("飞书撤回消息异常，消息ID：{}，错误：{}", 
                            platformMessageId, error.getMessage(), error);
                });
    }
    
    /**
     * 构建飞书消息请求体
     *
     * @param message 消息实体
     * @return 请求体
     */
    private Map<String, Object> buildMessageRequest(OfficeMessage message) {
        Map<String, Object> request = new HashMap<>();
        
        // 设置接收者
        String receiveIdType = getReceiveIdType(message.getRecipientType());
        request.put("receive_id_type", receiveIdType);
        
        // 如果是单个接收者
        if (message.getRecipientIds().size() == 1) {
            request.put("receive_id", message.getRecipientIds().get(0));
        }
        
        // 设置消息类型和内容
        String msgType = getMsgType(message.getMessageType());
        request.put("msg_type", msgType);
        
        // 构建消息内容
        Map<String, Object> content = buildMessageContent(message);
        request.put("content", content);
        
        return request;
    }
    
    /**
     * 获取飞书接收者ID类型
     *
     * @param recipientType 接收者类型
     * @return 飞书接收者ID类型
     */
    private String getReceiveIdType(RecipientType recipientType) {
        return switch (recipientType) {
            case USER -> "open_id";
            case DEPARTMENT -> "department_id";
            case GROUP -> "chat_id";
            case USER_LIST -> "open_id";
        };
    }
    
    /**
     * 获取飞书消息类型
     *
     * @param messageType 消息类型
     * @return 飞书消息类型
     */
    private String getMsgType(MessageType messageType) {
        return switch (messageType) {
            case TEXT -> "text";
            case MARKDOWN -> "interactive";
            case CARD -> "interactive";
            case IMAGE -> "image";
            case FILE -> "file";
        };
    }
    
    /**
     * 构建飞书消息内容
     *
     * @param message 消息实体
     * @return 消息内容
     */
    private Map<String, Object> buildMessageContent(OfficeMessage message) {
        Map<String, Object> content = new HashMap<>();
        
        if (message.getMessageType() == MessageType.TEXT) {
            content.put("text", message.getContent());
        } else if (message.getMessageType() == MessageType.MARKDOWN) {
            // Markdown消息需要转换为卡片格式
            Map<String, Object> elements = new HashMap<>();
            elements.put("tag", "markdown");
            elements.put("content", message.getContent());
            content.put("elements", new Object[]{elements});
        }
        
        return content;
    }
}'

echo ""
echo "3. 创建基础设施层 - 钉钉消息客户端..."
echo "================================================"

# 创建钉钉消息客户端
create_file "$PROJECT_ROOT/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/office/client/DingTalkMessageClient.java" 'package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.domain.office.message.MessageType;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.RecipientType;
import com.apex.scaffold.infrastructure.office.spi.OfficePlatformMessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉消息客户端实现
 *
 * <p>实现钉钉平台的消息发送功能，支持文本、Markdown、卡片等多种消息类型。
 *
 * <p>API文档：https://open.dingtalk.com/document/orgapp/asynchronous-sending-of-enterprise-session-messages
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkMessageClient implements OfficePlatformMessageClient {
    
    private final WebClient dingtalkWebClient;
    
    private static final String SEND_MESSAGE_URL = "/topapi/message/corpconversation/asyncsend_v2";
    private static final String RECALL_MESSAGE_URL = "/topapi/message/corpconversation/recall";
    
    @Override
    public String getPlatform() {
        return "DINGTALK";
    }
    
    @Override
    public Mono<String> sendMessage(OfficeMessage message) {
        log.info("钉钉发送消息，接收者类型：{}，接收者数量：{}", 
                message.getRecipientType(), message.getRecipientIds().size());
        
        return Mono.defer(() -> {
            Map<String, Object> requestBody = buildMessageRequest(message);
            
            return dingtalkWebClient.post()
                    .uri(SEND_MESSAGE_URL)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(response -> {
                        Integer errcode = (Integer) response.get("errcode");
                        if (errcode != null && errcode == 0) {
                            return (String) response.get("task_id");
                        } else {
                            String errmsg = (String) response.get("errmsg");
                            throw new RuntimeException("钉钉发送消息失败：" + errmsg);
                        }
                    });
        })
        .doOnSuccess(taskId -> {
            log.info("钉钉发送消息成功，任务ID：{}", taskId);
        })
        .doOnError(error -> {
            log.error("钉钉发送消息失败，错误：{}", error.getMessage(), error);
        });
    }
    
    @Override
    public Mono<String> sendBatchMessage(OfficeMessage message) {
        log.info("钉钉批量发送消息，接收者数量：{}", message.getRecipientIds().size());
        return sendMessage(message);
    }
    
    @Override
    public Mono<Boolean> recallMessage(String platformMessageId) {
        log.info("钉钉撤回消息，任务ID：{}", platformMessageId);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("msg_task_id", platformMessageId);
        
        return dingtalkWebClient.post()
                .uri(RECALL_MESSAGE_URL)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Integer errcode = (Integer) response.get("errcode");
                    return errcode != null && errcode == 0;
                })
                .doOnSuccess(recalled -> {
                    if (recalled) {
                        log.info("钉钉撤回消息成功");
                    } else {
                        log.warn("钉钉撤回消息失败");
                    }
                });
    }
    
    private Map<String, Object> buildMessageRequest(OfficeMessage message) {
        Map<String, Object> request = new HashMap<>();
        
        // 设置接收者
        if (message.getRecipientType() == RecipientType.USER || 
            message.getRecipientType() == RecipientType.USER_LIST) {
            request.put("userid_list", String.join(",", message.getRecipientIds()));
        } else if (message.getRecipientType() == RecipientType.DEPARTMENT) {
            request.put("dept_id_list", String.join(",", message.getRecipientIds()));
        }
        
        // 设置消息内容
        Map<String, Object> msg = new HashMap<>();
        msg.put("msgtype", getMsgType(message.getMessageType()));
        msg.put(getMsgType(message.getMessageType()), buildMessageContent(message));
        
        request.put("msg", msg);
        
        return request;
    }
    
    private String getMsgType(MessageType messageType) {
        return switch (messageType) {
            case TEXT -> "text";
            case MARKDOWN -> "markdown";
            case CARD -> "action_card";
            case IMAGE -> "image";
            case FILE -> "file";
        };
    }
    
    private Map<String, Object> buildMessageContent(OfficeMessage message) {
        Map<String, Object> content = new HashMap<>();
        
        if (message.getMessageType() == MessageType.TEXT) {
            content.put("content", message.getContent());
        } else if (message.getMessageType() == MessageType.MARKDOWN) {
            content.put("title", message.getTitle());
            content.put("text", message.getContent());
        }
        
        return content;
    }
}'

echo ""
echo "=========================================="
echo "代码生成完成！"
echo "=========================================="
echo -e "${GREEN}创建文件: $CREATED_FILES${NC}"
echo -e "${YELLOW}跳过文件: $SKIPPED_FILES${NC}"
echo -e "${BLUE}总文件数: $TOTAL_FILES${NC}"
echo ""

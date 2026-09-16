package com.apex.scaffold.application.office.message;

import com.apex.scaffold.domain.office.message.MessageType;
import com.apex.scaffold.domain.office.message.RecipientType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息命令
 *
 * <p>封装发送消息的请求参数，用于应用层处理消息发送业务逻辑。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageCommand {

    /**
     * 办公平台类型（FEISHU、DINGTALK、WECOM）
     */
    private String platform;

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 消息类型
     */
    private MessageType messageType;

    /**
     * 接收者类型
     */
    private RecipientType recipientType;

    /**
     * 接收者 ID 列表
     */
    private List<String> recipientIds;

    /**
     * 消息标题（可选）
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息附加数据（JSON 格式）
     */
    private String extraData;

    /**
     * 最大重试次数（默认 3 次）
     */
    private Integer maxRetryCount;
}

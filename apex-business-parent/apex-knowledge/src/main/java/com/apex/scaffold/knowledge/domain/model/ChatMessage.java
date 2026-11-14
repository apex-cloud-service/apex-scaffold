package com.apex.scaffold.knowledge.domain.model;

/**
 * 问答对话消息（发给模型的最小单元）。
 *
 * @param role 角色（system/user/assistant）
 * @param content 消息正文
 * @author Apex Team
 * @since 1.3.2
 */
public record ChatMessage(String role, String content) {

    /** system 角色常量。 */
    public static final String ROLE_SYSTEM = "system";

    /** user 角色常量。 */
    public static final String ROLE_USER = "user";

    /** assistant 角色常量。 */
    public static final String ROLE_ASSISTANT = "assistant";
}

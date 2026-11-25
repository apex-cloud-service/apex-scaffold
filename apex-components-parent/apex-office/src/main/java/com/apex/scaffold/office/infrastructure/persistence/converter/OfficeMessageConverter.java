package com.apex.scaffold.office.infrastructure.persistence.converter;

import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.office.domain.message.CardMessageContent;
import com.apex.scaffold.office.domain.message.MarkdownMessageContent;
import com.apex.scaffold.office.domain.message.MessageType;
import com.apex.scaffold.office.domain.message.OfficeMessage;
import com.apex.scaffold.office.domain.message.TextMessageContent;
import com.apex.scaffold.office.infrastructure.persistence.po.OfficeMessagePO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 办公消息转换器
 *
 * <p>负责领域实体（OfficeMessage）和持久化对象（OfficeMessagePO）之间的转换。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
@SuppressWarnings({"PMD.AbstractClassShouldStartWithAbstractNamingRule", "PMD.LowerCamelCaseVariableNamingRule"})
public abstract class OfficeMessageConverter {

    /** 消息接收者类型：用户。 */
    private static final String TARGET_TYPE_USER = "USER";
    /** 消息接收者类型：群组。 */
    private static final String TARGET_TYPE_GROUP = "GROUP";

    /**
     * JSON 序列化器。
     *
     * <p>MapStruct 生成的实现类无法声明带参构造器，故不依赖容器注入：直接复用
     * {@link JsonUtil#getObjectMapper()} 提供的线程安全共享实例，转换器可脱离容器独立使用。
     */
    private final ObjectMapper objectMapper = JsonUtil.getObjectMapper();

    /**
     * 领域实体转持久化对象
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    @Mapping(target = "messageType", expression = "java(messageTypeToString(entity.getMessageType()))")
    @Mapping(target = "recipientType", expression = "java(targetTypeFromEntity(entity.getTarget()))")
    @Mapping(target = "recipientIds", expression = "java(recipientIdsFromEntity(entity.getTarget()))")
    @Mapping(target = "content", expression = "java(contentToJson(entity.getContent()))")
    @Mapping(target = "status", source = "sendStatus")
    @Mapping(target = "resultMessage", source = "failureReason")
    public abstract OfficeMessagePO toPO(OfficeMessage entity);

    /**
     * 持久化对象转领域实体
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    @Mapping(target = "messageType", expression = "java(stringToMessageType(po.getMessageType()))")
    @Mapping(target = "target", expression = "java(targetFromPo(po))")
    @Mapping(target = "content", expression = "java(contentFromJson(po))")
    @Mapping(target = "sendStatus", source = "status")
    @Mapping(target = "failureReason", source = "resultMessage")
    public abstract OfficeMessage toEntity(OfficeMessagePO po);

    /**
     * 从领域消息目标中提取目标类型（用户或群组）。
     *
     * <p>供 MapStruct 表达式调用；目标为空时返回 {@code null}，对应持久化字段允许为空。
     *
     * @param target 领域层消息目标对象，允许为 {@code null}
     * @return 目标类型标识（{@code user} 或 {@code group}）；{@code target} 为空时返回 {@code null}
     */
    protected String targetTypeFromEntity(com.apex.scaffold.office.domain.message.MessageTarget target) {
        return target != null ? target.getTargetType() : null;
    }

    /**
     * 从领域消息目标中提取接收者标识列表并序列化为 JSON。
     *
     * <p>按目标类型选择 {@code userIds} 或 {@code groupIds}；目标或对应列表缺失时返回
     * {@code null}，避免写入无效的 {@code "null"} 字面量。
     *
     * @param target 领域层消息目标对象，允许为 {@code null}
     * @return 接收者标识列表的 JSON 字符串；无法提取时返回 {@code null}
     */
    protected String recipientIdsFromEntity(com.apex.scaffold.office.domain.message.MessageTarget target) {
        if (target == null) {
            return null;
        }

        if (TARGET_TYPE_USER.equals(target.getTargetType()) && target.getUserIds() != null) {
            return listToJson(target.getUserIds());
        } else if (TARGET_TYPE_GROUP.equals(target.getTargetType()) && target.getGroupIds() != null) {
            return listToJson(target.getGroupIds());
        }

        return null;
    }

    /**
     * 从持久化对象还原领域消息目标。
     *
     * <p>接收者类型为空时返回 {@code null}；接收者标识列按 JSON 数组反序列化，
     * 并按用户/群组类型分别装配到 {@link com.apex.scaffold.office.domain.message.MessageTarget}。
     *
     * @param po 办公消息持久化对象
     * @return 领域消息目标；接收者类型未知或为空时返回 {@code null}
     */
    protected com.apex.scaffold.office.domain.message.MessageTarget targetFromPo(OfficeMessagePO po) {
        if (po.getRecipientType() == null) {
            return null;
        }

        List<String> ids = jsonToList(po.getRecipientIds());

        if (TARGET_TYPE_USER.equals(po.getRecipientType())) {
            return com.apex.scaffold.office.domain.message.MessageTarget.builder()
                    .targetType(TARGET_TYPE_USER)
                    .userIds(ids)
                    .build();
        } else if (TARGET_TYPE_GROUP.equals(po.getRecipientType())) {
            return com.apex.scaffold.office.domain.message.MessageTarget.builder()
                    .targetType(TARGET_TYPE_GROUP)
                    .groupIds(ids)
                    .build();
        }

        return null;
    }

    /**
     * 从持久化对象恢复领域消息内容。
     *
     * <p>按消息类型把 JSON 负载还原为文本、Markdown 或卡片内容对象；兼容
     * {@code {"data": {...}}} 包装与裸负载两种历史格式。负载缺失时返回 {@code null}。
     *
     * @param po 办公消息持久化对象，其 {@code content} 列为 JSON 字符串
     * @return 对应的领域消息内容；内容为空时返回 {@code null}
     * @throws IllegalArgumentException 内容 JSON 无法解析时抛出
     */
    protected com.apex.scaffold.office.domain.message.MessageContent contentFromJson(OfficeMessagePO po) {
        if (po.getContent() == null || po.getContent().isBlank()) {
            return null;
        }
        try {
            MessageType type = stringToMessageType(po.getMessageType());
            JsonNode node = objectMapper.readTree(po.getContent());
            JsonNode data = node.has("data") ? node.get("data") : node;
            return switch (type) {
                case TEXT -> TextMessageContent.builder()
                        .text(data.has("text") ? data.get("text").asText() : data.asText())
                        .build();
                case MARKDOWN -> MarkdownMessageContent.builder()
                        .markdown(data.has("markdown") ? data.get("markdown").asText() : data.asText())
                        .title(data.has("title") ? data.get("title").asText() : null)
                        .build();
                case CARD -> CardMessageContent.builder()
                        .cardJson(data.has("cardJson") ? data.get("cardJson").asText() : data.asText())
                        .title(data.has("title") ? data.get("title").asText() : null)
                        .cardType(data.has("cardType") ? data.get("cardType").asText() : null)
                        .build();
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("办公消息内容格式无效", e);
        }
    }

    /**
     * 将领域消息内容序列化为 JSON 存储值。
     *
     * @param content 领域消息内容对象，允许为 {@code null}
     * @return JSON 字符串；{@code content} 为空时返回 {@code null}
     * @throws IllegalArgumentException 序列化失败时抛出
     */
    protected String contentToJson(com.apex.scaffold.office.domain.message.MessageContent content) {
        if (content == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("办公消息内容无法序列化", e);
        }
    }

    /**
     * 消息类型枚举转字符串。
     *
     * @param messageType 消息类型枚举值，允许为 {@code null}
     * @return 枚举名称；入参为 {@code null} 时返回 {@code null}
     */
    protected String messageTypeToString(MessageType messageType) {
        return messageType != null ? messageType.name() : null;
    }

    /**
     * 字符串转消息类型枚举。
     *
     * <p>按枚举名称精确匹配；无法识别的历史取值返回 {@code null} 而非抛异常，
     * 以便兼容存量脏数据。
     *
     * @param messageType 数据库中存储的枚举名称，允许为 {@code null}
     * @return 对应枚举值；入参为空或无法识别时返回 {@code null}
     */
    protected MessageType stringToMessageType(String messageType) {
        try {
            return messageType != null ? MessageType.valueOf(messageType) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 字符串列表序列化为 JSON 数组字符串。
     *
     * @param list 待序列化列表，允许为 {@code null} 或空
     * @return JSON 数组字符串；入参为空或序列化失败时返回 {@code null}
     */
    protected String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JSON 数组字符串反序列化为字符串列表。
     *
     * @param json 存储的 JSON 数组字符串，允许为 {@code null} 或空串
     * @return 字符串列表；入参为空或解析失败时返回 {@code null}
     */
    protected List<String> jsonToList(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}

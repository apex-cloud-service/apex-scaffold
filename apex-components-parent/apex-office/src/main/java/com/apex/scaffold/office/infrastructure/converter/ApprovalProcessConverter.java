package com.apex.scaffold.office.infrastructure.converter;

import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.domain.approval.ApprovalRecord;
import com.apex.scaffold.office.domain.approval.ApprovalStatus;
import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 审批流程转换器
 *
 * <p>使用MapStruct实现领域实体与持久化对象之间的转换，避免手写样板代码。
 * 处理复杂字段转换：
 * <ul>
 *   <li>枚举类型转换（OfficePlatform、ApprovalStatus）</li>
 *   <li>JSON序列化/反序列化（审批记录列表、审批人列表）</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
@SuppressWarnings({"PMD.AbstractClassShouldStartWithAbstractNamingRule", "PMD.LowerCamelCaseVariableNamingRule"})
public abstract class ApprovalProcessConverter {

    /**
     * JSON 序列化器。
     *
     * <p>MapStruct 生成的实现类只能声明无参构造器，无法转发放置在抽象基类上的带参构造器，
     * 故不依赖容器注入：直接复用 {@link JsonUtil#getObjectMapper()} 提供的线程安全共享实例，
     * 转换器可脱离容器独立使用。
     */
    private final ObjectMapper objectMapper = JsonUtil.getObjectMapper();

    /**
     * 领域实体转PO
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    @Mapping(target = "platform", source = "platform", qualifiedByName = "platformToString")
    @Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
    @Mapping(target = "currentApproverIds", source = "currentApproverIds")
    @Mapping(target = "records", source = "records", qualifiedByName = "recordsToJson")
    @Mapping(target = "processName", source = "title")
    @Mapping(target = "createdAt", source = "createTime")
    public abstract ApprovalProcessPO toPO(ApprovalProcess entity);

    /**
     * PO转领域实体
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    @Mapping(target = "platform", source = "platform", qualifiedByName = "stringToPlatform")
    @Mapping(target = "status", source = "status", qualifiedByName = "stringToStatus")
    @Mapping(target = "currentApproverIds", source = "currentApproverIds")
    @Mapping(target = "records", source = "records", qualifiedByName = "jsonToRecords")
    @Mapping(target = "title", source = "processName")
    @Mapping(target = "createTime", source = "createdAt")
    public abstract ApprovalProcess toEntity(ApprovalProcessPO po);

    /**
     * 办公平台枚举转持久化字符串。
     *
     * @param platform 办公平台枚举值，允许为 {@code null}
     * @return 枚举名称；入参为 {@code null} 时返回 {@code null}
     */
    @Named("platformToString")
    protected String platformToString(OfficePlatform platform) {
        return platform != null ? platform.name() : null;
    }

    /**
     * 持久化字符串转办公平台枚举。
     *
     * @param platform 数据库中存储的枚举名称，允许为 {@code null}
     * @return 对应枚举值；入参为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException 名称不属于 {@link OfficePlatform} 时抛出
     */
    @Named("stringToPlatform")
    protected OfficePlatform stringToPlatform(String platform) {
        return platform != null ? OfficePlatform.valueOf(platform) : null;
    }

    /**
     * 审批状态枚举转持久化字符串。
     *
     * @param status 审批状态枚举值，允许为 {@code null}
     * @return 枚举名称；入参为 {@code null} 时返回 {@code null}
     */
    @Named("statusToString")
    protected String statusToString(ApprovalStatus status) {
        return status != null ? status.name() : null;
    }

    /**
     * 持久化字符串转审批状态枚举。
     *
     * @param status 数据库中存储的枚举名称，允许为 {@code null}
     * @return 对应枚举值；入参为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException 名称不属于 {@link ApprovalStatus} 时抛出
     */
    @Named("stringToStatus")
    protected ApprovalStatus stringToStatus(String status) {
        return status != null ? ApprovalStatus.valueOf(status) : null;
    }

    /**
     * 字符串列表序列化为 JSON 数组字符串。
     *
     * @param list 待序列化列表，允许为 {@code null} 或空
     * @return JSON 数组字符串；入参为空或序列化失败时返回 {@code null}
     */
    @Named("listToJson")
    protected String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * JSON 数组字符串反序列化为字符串列表。
     *
     * @param json 存储的 JSON 数组字符串，允许为 {@code null} 或空串
     * @return 字符串列表；入参为空或解析失败时返回空列表而非 {@code null}
     */
    @Named("jsonToList")
    protected List<String> jsonToList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    /**
     * 审批记录列表序列化为 JSON 数组字符串。
     *
     * @param records 审批记录列表，允许为 {@code null} 或空
     * @return JSON 数组字符串；入参为空或序列化失败时返回 {@code null}
     */
    @Named("recordsToJson")
    protected String recordsToJson(List<ApprovalRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(records);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * JSON 数组字符串反序列化为审批记录列表。
     *
     * @param json 存储的 JSON 数组字符串，允许为 {@code null} 或空串
     * @return 审批记录列表；入参为空或解析失败时返回空列表而非 {@code null}
     */
    @Named("jsonToRecords")
    protected List<ApprovalRecord> jsonToRecords(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ApprovalRecord>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}

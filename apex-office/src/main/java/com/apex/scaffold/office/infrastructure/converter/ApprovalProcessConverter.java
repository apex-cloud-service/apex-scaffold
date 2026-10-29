package com.apex.scaffold.office.infrastructure.converter;

import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.domain.approval.ApprovalRecord;
import com.apex.scaffold.office.domain.approval.ApprovalStatus;
import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
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
     * 故沿用本模块 {@code OfficeMessageConverter} 的做法：JSR-250 {@code @Resource} 字段注入，
     * 避免使用 Spring {@code @Autowired}，同时保留默认实例以便脱离容器（如单元测试）时可用。
     */
    @Resource
    private ObjectMapper objectMapper = new ObjectMapper();

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
    public abstract ApprovalProcess toEntity(ApprovalProcessPO po);

    /**
     * 平台枚举转字符串
     */
    @Named("platformToString")
    protected String platformToString(OfficePlatform platform) {
        return platform != null ? platform.name() : null;
    }

    /**
     * 字符串转平台枚举
     */
    @Named("stringToPlatform")
    protected OfficePlatform stringToPlatform(String platform) {
        return platform != null ? OfficePlatform.valueOf(platform) : null;
    }

    /**
     * 状态枚举转字符串
     */
    @Named("statusToString")
    protected String statusToString(ApprovalStatus status) {
        return status != null ? status.name() : null;
    }

    /**
     * 字符串转状态枚举
     */
    @Named("stringToStatus")
    protected ApprovalStatus stringToStatus(String status) {
        return status != null ? ApprovalStatus.valueOf(status) : null;
    }

    /**
     * 列表转JSON字符串
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
     * JSON字符串转列表
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
     * 审批记录列表转JSON
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
     * JSON转审批记录列表
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

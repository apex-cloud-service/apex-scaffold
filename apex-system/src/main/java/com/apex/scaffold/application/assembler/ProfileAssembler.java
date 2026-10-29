package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.MeVO;
import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.domain.model.entity.UserComplianceEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 用户资料对象转换器
 *
 * <p>使用 MapStruct 实现用户资料领域实体与视图对象之间的自动转换
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户资料聚合：将UserEntity、UserProfileEntity、UserComplianceEntity聚合成MeVO</li>
 *   <li>字段映射：自动映射用户基础信息、资料信息、合规设置到视图对象</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>teenMode：根据合规实体的teenMode字段判断青少年模式状态</li>
 *   <li>socialUser：根据资料实体的socialUser字段判断社交用户类型</li>
 *   <li>sameFrequency/tags：直接从参数传入，不参与实体转换</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MeVO
 * @see UserEntity
 * @see UserProfileEntity
 * @see UserComplianceEntity
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProfileAssembler {

    /**
     * 构建"我的资料"视图对象。
     *
     * @param user          当前用户实体
     * @param profile       用户资料实体
     * @param compliance    用户合规设置实体
     * @param sameFrequency 同频解锁与状态信息
     * @param tags          标签集合（个性/兴趣/外貌等）
     * @return 我的资料视图对象
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "nickName", source = "user.nickName")
    @Mapping(target = "gender", source = "user.gender")
    @Mapping(target = "city", source = "user.city")
    @Mapping(target = "heightCm", source = "profile.heightCm")
    @Mapping(target = "educationLevel", source = "profile.educationLevel")
    @Mapping(target = "teenMode", source = "compliance", qualifiedByName = "complianceToTeenMode")
    @Mapping(target = "socialUser", source = "profile", qualifiedByName = "profileToSocialUser")
    @Mapping(target = "sameFrequency", source = "sameFrequency")
    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "openId", source = "user.openId")
    @Mapping(target = "unionId", source = "user.unionId")
    @Mapping(target = "birthday", source = "user.birthday", dateFormat = "yyyy-MM-dd")
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "apexVip", ignore = true)
    @Mapping(target = "vipLevel", ignore = true)
    @Mapping(target = "verificationStatus", ignore = true)
    @Mapping(target = "verificationCompletedCount", ignore = true)
    @Mapping(target = "verificationRequiredCount", ignore = true)
    @Mapping(target = "receivedLikeCount", ignore = true)
    @Mapping(target = "creditBalance", ignore = true)
    @Mapping(target = "aestheticResonanceCount", ignore = true)
    @Mapping(target = "exposureCreditBalance", ignore = true)
    MeVO toMeVO(
            UserEntity user,
            UserProfileEntity profile,
            UserComplianceEntity compliance,
            Map<String, Object> sameFrequency,
            Map<String, Object> tags);

    /**
     * 从用户合规实体中提取青少年模式状态。
     *
     * <p>若 compliance 为 null 或其 teenMode 字段为 null，返回 null；
     * 若 teenMode 等于 {@link SystemConstants#BOOLEAN_TRUE} 则返回 true，否则返回 false。
     *
     * @param compliance 用户合规设置实体
     * @return 青少年模式布尔值，null 表示无法判断
     */
    @Named("complianceToTeenMode")
    default Boolean complianceToTeenMode(UserComplianceEntity compliance) {
        if (compliance == null || compliance.getTeenMode() == null) {
            return null;
        }
        return compliance.getTeenMode() == SystemConstants.BOOLEAN_TRUE;
    }

    /**
     * 从用户资料实体中提取社交用户状态。
     *
     * <p>若 profile 为 null 或其 socialUser 字段为 null，返回 null；
     * 若 socialUser 等于 {@link SystemConstants#BOOLEAN_TRUE} 则返回 true，否则返回 false。
     *
     * @param profile 用户资料实体
     * @return 社交用户布尔值，null 表示无法判断
     */
    @Named("profileToSocialUser")
    default Boolean profileToSocialUser(UserProfileEntity profile) {
        if (profile == null || profile.getSocialUser() == null) {
            return null;
        }
        return profile.getSocialUser() == SystemConstants.BOOLEAN_TRUE;
    }
}

package com.apex.scaffold.application.factory;

import com.apex.scaffold.domain.model.entity.UserComplianceEntity;
import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import org.springframework.stereotype.Component;

/**
 * 用户默认值工厂：为新注册用户创建默认的用户画像与合规记录。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class UserDefaultsFactory {

    /**
     * 创建默认用户画像。
     *
     * @param userId 用户 ID
     * @return 默认用户画像实体
     */
    public UserProfileEntity createDefaultProfile(Long userId) {
        return UserProfileEntity.create(userId);
    }

    /**
     * 创建默认用户合规记录。
     *
     * @param userId 用户 ID
     * @return 默认合规记录实体
     */
    public UserComplianceEntity createDefaultCompliance(Long userId) {
        return UserComplianceEntity.create(userId);
    }
}

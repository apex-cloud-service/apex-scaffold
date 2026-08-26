package com.apex.scaffold.domain.service;

import com.apex.scaffold.domain.model.entity.UserConsentEntity;

/**
 * 用户协议同意事务写入端口。
 *
 * <p>定义用户首次登录时同意用户协议和隐私政策的原子写入操作。
 * 在单事务中同时保存两条同意记录，确保用户协议与隐私政策的
 * 同意状态一致，避免出现"同意了协议但未同意隐私政策"的中间态。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.1.0
 */
public interface ConsentCommandPort {

    /**
     * 在同一事务中保存用户协议和隐私政策记录。
     *
     * @param userAgreement 用户协议记录
     * @param privacyPolicy 隐私政策记录
     */
    void saveBoth(UserConsentEntity userAgreement, UserConsentEntity privacyPolicy);
}

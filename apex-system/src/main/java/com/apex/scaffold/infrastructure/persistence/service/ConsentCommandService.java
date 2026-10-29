package com.apex.scaffold.infrastructure.persistence.service;

import com.apex.scaffold.domain.model.entity.UserConsentEntity;
import com.apex.scaffold.domain.service.ConsentCommandPort;
import com.apex.scaffold.infrastructure.persistence.converter.UserConsentPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.UserConsentMapper;
import com.apex.scaffold.infrastructure.persistence.po.UserConsentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户协议同意事务写入服务。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.1.0
 */
@Service
@RequiredArgsConstructor
public class ConsentCommandService implements ConsentCommandPort {

    private final UserConsentMapper mapper;
    private final UserConsentPoConverter converter;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBoth(UserConsentEntity userAgreement, UserConsentEntity privacyPolicy) {
        insert(userAgreement);
        insert(privacyPolicy);
    }

    /**
     * 插入单条同意记录并校验结果。
     *
     * @param entity 同意记录
     */
    private void insert(UserConsentEntity entity) {
        UserConsentPO po = converter.toPo(entity);
        if (mapper.insert(po) != 1) {
            throw new IllegalStateException("保存用户协议同意记录失败, policyType=" + entity.getPolicyType());
        }
    }
}

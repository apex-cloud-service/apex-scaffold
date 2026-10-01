package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * JWT配置持久化对象
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_jwt_config")
public class SysJwtConfigPO extends BasePO {

    private String issuer;

    private String secretKey;

    private Integer accessTokenTtl;

    private Integer refreshTokenTtl;

    private String algorithm;

    private String publicKey;

    private String privateKey;

    private Integer enabled;

    private String tenantId;
}

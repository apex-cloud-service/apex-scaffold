package com.apex.scaffold.integration.connector.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户级外部凭据持久化对象（表 {@code int_credential}）。
 *
 * <p>密文（AES-256-GCM）与 IV 分列存储；明文不落库。{@code versionNo} 为业务轮换版本
 * （非乐观锁），随轮换递增。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("int_credential")
public class CredentialPO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 凭据标识（租户内唯一）。 */
    private String credentialKey;

    /** 凭据类型（API_KEY/BASIC/TOKEN/X509）。 */
    private String credentialType;

    /** 密文（AES-256-GCM，Base64；明文不落库）。 */
    private String secretCipher;

    /** 加密 IV（Base64）。 */
    private String secretIv;

    /** 凭据版本（轮换递增）。 */
    private Integer versionNo;

    /** 最近轮换时间。 */
    private LocalDateTime rotatedAt;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;

    /** 更新时间（DB ON UPDATE 填充）。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}

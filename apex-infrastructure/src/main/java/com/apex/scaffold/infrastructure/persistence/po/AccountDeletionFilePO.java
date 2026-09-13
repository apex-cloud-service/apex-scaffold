package com.apex.scaffold.infrastructure.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 账号注销阶段待物理删除的非生活照对象位置。
 *
 * <p>记录对象存储桶与对象名，用于异步物理删除任务的定位；不含业务字段。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
/**n * AccountDeletionFilePO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class AccountDeletionFilePO {

    private String bucketName;

    private String fileName;
}

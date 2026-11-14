package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 代码生成产物持久化对象。
 *
 * <p>对应数据库表 {@code gen_artifact}，记录向导受控执行写入任务目录的单个文件，
 * 是产物下载的唯一定位依据。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.generator.GenArtifactEntity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("gen_artifact")
public class GenArtifactPO extends BasePO {

    /** 同一次执行产生的产物共享同一任务 ID。 */
    private Long taskId;

    /** 生成配置 ID。 */
    private Long configId;

    /** 生成历史 ID。 */
    private Long historyId;

    /** 来源表名。 */
    private String tableName;

    /** 模板 ID。 */
    private Long templateId;

    /** 模板类型。 */
    private String templateType;

    /** 相对任务目录的输出路径。 */
    private String relativePath;

    /** 文件名。 */
    private String fileName;

    /** 文件大小（字节）。 */
    private Long fileSize;

    /** 内容 SHA-256 摘要（前 16 位）。 */
    private String contentDigest;

    /** 发布状态（PUBLISHED/FAILED）。 */
    private String status;

    /** 失败原因。 */
    private String errorMessage;

    /** 租户标识。 */
    private String tenantId;
}

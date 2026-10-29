package com.apex.scaffold.domain.model.generator;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 代码生成产物实体（代码生成器领域模型）
 *
 * <p>记录向导受控执行时写入独立任务目录的单个文件，是浏览器下载的唯一入口。
 * 产物与生成历史、配置、模板关联，保留租户归属，下载前必须重新校验归属与磁盘路径。
 *
 * <h2>发布状态</h2>
 * <ul>
 *   <li><strong>PUBLISHED</strong>：文件已成功写入任务目录</li>
 *   <li><strong>FAILED</strong>：写入失败，失败原因记录在 {@link #errorMessage}</li>
 * </ul>
 *
 * <h2>安全约束</h2>
 * <ul>
 *   <li>{@link #relativePath} 只能是任务目录内的相对路径，不得包含绝对路径或 {@code ..}</li>
 *   <li>下载按 {@link #getId()} 定位，不接受客户端提交任意文件路径</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenArtifactEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 同一次执行产生的产物共享同一任务 ID。 */
    private Long taskId;

    private Long configId;

    private Long historyId;

    private String tableName;

    private Long templateId;

    private String templateType;

    private String relativePath;

    private String fileName;

    private Long fileSize;

    private String contentDigest;

    private String status;

    private String errorMessage;

    private String tenantId;
}

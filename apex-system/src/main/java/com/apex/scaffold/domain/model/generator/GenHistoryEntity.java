package com.apex.scaffold.domain.model.generator;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 生成历史实体（代码生成器领域模型）
 *
 * <p>记录每次代码生成任务的执行结果，包括生成的文件列表、输出路径、执行状态和错误信息。
 * 生成历史用于审计追踪、问题排查和回滚操作。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>记录生成任务：关联生成配置，记录执行时间和操作人</li>
 *   <li>追踪生成文件：记录所有生成的文件路径，便于管理和清理</li>
 *   <li>记录执行状态：成功（SUCCESS）或失败（FAILED）</li>
 *   <li>保存错误信息：失败时记录异常堆栈和错误描述</li>
 * </ul>
 *
 * <h2>状态定义</h2>
 * <ul>
 *   <li><strong>SUCCESS</strong>：代码生成成功，所有文件已写入</li>
 *   <li><strong>FAILED</strong>：代码生成失败，可能是数据库连接失败、模板解析错误、
 *       文件写入失败等，详细错误记录在 {@link #errorMessage} 中</li>
 * </ul>
 *
 * <h2>生成文件列表</h2>
 * <p>{@link #generatedFiles} 字段存储 JSON 数组格式的文件路径列表，示例：
 * <pre>{@code
 * [
 *   "/project/src/main/java/com/apex/scaffold/domain/model/UserEntity.java",
 *   "/project/src/main/java/com/apex/scaffold/infrastructure/mapper/UserMapper.java",
 *   "/project/src/main/resources/mapper/UserMapper.xml"
 * ]
 * }</pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li><strong>审计追踪</strong>：记录谁在何时生成了哪些代码</li>
 *   <li><strong>问题排查</strong>：失败时查看错误信息定位问题</li>
 *   <li><strong>文件管理</strong>：根据历史记录清理或回滚生成的文件</li>
 *   <li><strong>统计分析</strong>：统计生成频率、成功率等指标</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <ol>
 *   <li>创建：代码生成任务开始时创建历史记录，状态初始为待处理</li>
 *   <li>更新：生成完成后更新状态为 SUCCESS 或 FAILED，记录生成文件或错误信息</li>
 *   <li>查询：分页查询历史记录，按时间倒序展示</li>
 *   <li>删除：逻辑删除（deleted=1），不影响已生成的代码文件</li>
 * </ol>
 *
 * <h2>关联关系</h2>
 * <ul>
 *   <li>多对一：多条历史记录对应同一个生成配置（{@code GenConfigEntity}）</li>
 *   <li>多对一：多条历史记录对应同一个操作人（系统用户）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenConfigEntity
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenHistoryEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public GenHistoryEntity() {
        super();
    }

    private Long configId;

    private String generatedFiles;

    private String outputPath;

    private String status;

    private String errorMessage;

    private Long operatorId;

    private String tenantId;
}

package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 代码生成历史记录持久化对象。
 *
 * <p>对应数据库表 {@code gen_history},记录每次代码生成操作的执行结果,
 * 包括生成的文件清单、输出路径、执行状态、错误信息和操作人等审计信息。
 *
 * <p><strong>主要字段</strong></p>
 * <ul>
 *   <li>{@link #configId} - 关联的生成配置ID</li>
 *   <li>{@link #generatedFiles} - 生成的文件清单(JSON 数组)</li>
 *   <li>{@link #outputPath} - 实际输出路径(快照)</li>
 *   <li>{@link #status} - 生成状态(SUCCESS/FAILED)</li>
 *   <li>{@link #errorMessage} - 错误信息(失败时记录)</li>
 *   <li>{@link #operatorId} - 操作人用户ID</li>
 *   <li>{@link #tenantId} - 租户标识</li>
 * </ul>
 *
 * <p><strong>审计作用</strong></p>
 * <ul>
 *   <li>追溯代码生成历史,支持回溯和对比</li>
 *   <li>统计生成成功率和失败原因</li>
 *   <li>定位问题配置和模板错误</li>
 *   <li>提供操作审计和责任追踪</li>
 * </ul>
 *
 * <p><strong>JSON 字段格式</strong></p>
 * <p>{@code generatedFiles} 示例:
 * <pre>{@code
 * [
 *   {
 *     "fileName": "UserEntity.java",
 *     "filePath": "/Users/work/output/domain/model/user/UserEntity.java",
 *     "fileSize": 2048,
 *     "lineCount": 65
 *   },
 *   {
 *     "fileName": "UserMapper.java",
 *     "filePath": "/Users/work/output/infrastructure/mapper/UserMapper.java",
 *     "fileSize": 1024,
 *     "lineCount": 32
 *   }
 * ]
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.generator.GenHistoryEntity
 * @see GenConfigPO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("gen_history")
public class GenHistoryPO extends BasePO {

    /**
     * 生成配置ID。
     *
     * <p>外键关联 {@code gen_config.id},标识本次生成使用的配置。
     * 通过该字段可以查询某个配置的所有历史生成记录,分析配置的使用情况和成功率。
     *
     * <p>如果配置被删除,历史记录通常保留以维护审计完整性,但该字段会成为孤儿引用。
     */
    private Long configId;

    /**
     * 生成的文件清单(JSON 数组格式)。
     *
     * <p>记录本次生成操作输出的所有文件详情,每个文件包含:
     * <ul>
     *   <li>{@code fileName} - 文件名(含扩展名)</li>
     *   <li>{@code filePath} - 完整文件路径(绝对路径)</li>
     *   <li>{@code fileSize} - 文件大小(字节)</li>
     *   <li>{@code lineCount} - 代码行数</li>
     * </ul>
     *
     * <p>该清单用于:
     * <ul>
     *   <li>展示生成结果摘要(共生成 X 个文件,Y 行代码)</li>
     *   <li>支持文件级别的查看和下载</li>
     *   <li>检测文件覆盖冲突</li>
     * </ul>
     */
    private String generatedFiles;

    /**
     * 实际输出路径(快照)。
     *
     * <p>记录本次生成时使用的输出路径,作为配置的历史快照。
     * 因为配置的 {@code outputPath} 可能会修改,此字段保留执行时的真实值,便于追溯。
     *
     * <p>示例:
     * <ul>
     *   <li>{@code /Users/work/apex-scaffold/output}</li>
     *   <li>{@code /tmp/apex-gen-20260919}</li>
     * </ul>
     *
     * <p>长度限制 500 字符。
     */
    private String outputPath;

    /**
     * 生成状态。
     *
     * <p>标识本次代码生成的执行结果:
     * <ul>
     *   <li>{@code SUCCESS} - 成功:所有文件正常生成并写入</li>
     *   <li>{@code FAILED} - 失败:生成过程中出现错误,部分或全部文件未能生成</li>
     * </ul>
     *
     * <p>状态为 {@code FAILED} 时,{@link #errorMessage} 字段会记录详细错误信息。
     *
     * <p>长度限制 20 字符。
     */
    private String status;

    /**
     * 错误信息。
     *
     * <p>当 {@link #status} 为 {@code FAILED} 时,记录详细的错误原因,包括:
     * <ul>
     *   <li>数据库连接失败:{@code "无法连接到数据源 xxx:Connection refused"}</li>
     *   <li>表不存在:{@code "表 sys_unknown 不存在于数据库中"}</li>
     *   <li>模板渲染错误:{@code "FreeMarker 模板解析失败:未定义变量 tableName"}</li>
     *   <li>文件写入失败:{@code "无法写入文件 /xxx/UserEntity.java:Permission denied"}</li>
     *   <li>路径校验失败:{@code "输出路径包含非法字符 .. ,禁止访问"}</li>
     * </ul>
     *
     * <p>错误信息应包含堆栈跟踪的关键部分,但避免输出敏感信息(如密码、密钥)。
     *
     * <p>类型为 TEXT,支持存储较长的异常堆栈信息。
     */
    private String errorMessage;

    /**
     * 操作人用户ID。
     *
     * <p>记录执行代码生成操作的用户ID,关联 {@code sys_user.id}。
     * 用于审计追踪,明确操作责任人。
     *
     * <p>在自动化场景(如定时任务、CI/CD 流水线触发)中,可以使用系统用户ID。
     */
    private Long operatorId;

    /**
     * 租户标识。
     *
     * <p>实现多租户数据隔离,确保不同租户的生成历史相互独立。
     * 查询历史记录时必须携带租户标识进行过滤。
     *
     * <p>长度限制 64 字符。
     */
    private String tenantId;
}

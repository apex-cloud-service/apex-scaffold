package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文档持久化对象。
 *
 * <p>对应数据库表 {@code doc_document}，存储文档的持久化数据。
 * 使用 MyBatis-Plus 注解映射数据库表结构，支持自动生成主键、JSON 字段映射、
 * 乐观锁、逻辑删除等特性。
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>id：主键，自增 ID</li>
 *   <li>title：文档标题</li>
 *   <li>docType：文档类型（合同/报告/提案/手册/其他）</li>
 *   <li>category：文档分类（技术/业务/财务/人力）</li>
 *   <li>content：文档内容</li>
 *   <li>fileUrl：附件文件 URL</li>
 *   <li>fileSize：附件文件大小（字节）</li>
 *   <li>authorId：作者用户 ID</li>
 *   <li>authorName：作者用户名</li>
 *   <li>status：文档状态（0=草稿，1=正常，2=归档）</li>
 *   <li>tags：文档标签（JSON 数组，使用 {@link JacksonTypeHandler} 映射）</li>
 *   <li>viewCount：浏览次数</li>
 *   <li>downloadCount：下载次数</li>
 *   <li>tenantId：租户 ID（多租户隔离字段）</li>
 * </ul>
 *
 * <h2>继承关系</h2>
 * <p>继承 {@link BasePO}，自动获得创建时间、更新时间、版本号、逻辑删除等通用字段。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BasePO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName(value = "doc_document", autoResultMap = true)
public class DocumentPO extends BasePO {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String docType;

    private String category;

    private String content;

    private String fileUrl;

    private Long fileSize;

    private Long authorId;

    private String authorName;

    private Integer status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private Integer viewCount;

    private Integer downloadCount;

    private String tenantId;
}

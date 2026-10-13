package com.apex.scaffold.office.infrastructure.mapper;

import com.apex.scaffold.office.infrastructure.po.DocumentInfoPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档信息Mapper接口
 *
 * <p>提供文档信息的数据库访问操作，继承MyBatis-Plus的BaseMapper获得基础CRUD能力。
 * 自定义查询方法使用@Select注解定义SQL语句。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Mapper
public interface DocumentInfoMapper extends BaseMapper<DocumentInfoPO> {

    /**
     * 根据平台和平台文档ID查询文档信息
     *
     * @param platform 办公平台类型
     * @param platformDocumentId 平台中的文档ID
     * @return 文档信息PO
     */
    @Select(
            "SELECT * FROM office_document_info WHERE platform = #{platform} AND platform_document_id = #{platformDocumentId} AND deleted = 0")
    DocumentInfoPO selectByPlatformAndDocumentId(
            @Param("platform") String platform, @Param("platformDocumentId") String platformDocumentId);

    /**
     * 查询指定所有者的文档列表
     *
     * @param ownerId 所有者ID
     * @return 文档信息列表
     */
    @Select("SELECT * FROM office_document_info WHERE owner_id = #{ownerId} AND deleted = 0 ORDER BY create_time DESC")
    List<DocumentInfoPO> selectByOwner(@Param("ownerId") String ownerId);

    /**
     * 查询指定父文件夹下的文档列表
     *
     * @param parentFolderId 父文件夹ID
     * @return 文档信息列表
     */
    @Select(
            "SELECT * FROM office_document_info WHERE parent_folder_id = #{parentFolderId} AND deleted = 0 ORDER BY is_folder DESC, title ASC")
    List<DocumentInfoPO> selectByParentFolder(@Param("parentFolderId") String parentFolderId);

    /**
     * 根据文档标题模糊查询
     *
     * @param titleKeyword 标题关键字
     * @return 文档信息列表
     */
    @Select(
            "SELECT * FROM office_document_info WHERE title LIKE CONCAT('%', #{titleKeyword}, '%') AND deleted = 0 ORDER BY create_time DESC")
    List<DocumentInfoPO> searchByTitle(@Param("titleKeyword") String titleKeyword);

    /**
     * 查询指定协作者参与的文档列表
     *
     * @param collaboratorId 协作者ID
     * @return 文档信息列表
     */
    @Select(
            "SELECT * FROM office_document_info WHERE collaborator_ids LIKE CONCAT('%', #{collaboratorId}, '%') AND deleted = 0 ORDER BY create_time DESC")
    List<DocumentInfoPO> selectByCollaborator(@Param("collaboratorId") String collaboratorId);

    /**
     * 查询已归档的文档列表
     *
     * @param ownerId 所有者ID
     * @return 文档信息列表
     */
    @Select(
            "SELECT * FROM office_document_info WHERE owner_id = #{ownerId} AND is_archived = 1 AND deleted = 0 ORDER BY create_time DESC")
    List<DocumentInfoPO> selectArchivedDocuments(@Param("ownerId") String ownerId);
}

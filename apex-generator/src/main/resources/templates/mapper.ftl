package ${infraPackage}.persistence.mapper;

import ${infraPackage}.persistence.po.${entityName}PO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ${tableComment} MyBatis Mapper 接口。
 *
 * <p>负责 {@code ${tableName}} 表的持久化操作。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface ${entityName}Mapper extends BaseMapper<${entityName}PO> {}

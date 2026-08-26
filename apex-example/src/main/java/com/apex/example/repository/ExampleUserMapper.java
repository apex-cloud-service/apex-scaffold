package com.apex.example.repository;

import com.apex.example.model.ExampleUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 示例用户Mapper
 *
 * <p>继承MyBatis-Plus的BaseMapper,自动获得CRUD方法
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Mapper
public interface ExampleUserMapper extends BaseMapper<ExampleUser> {
    // BaseMapper已提供常用CRUD方法,无需额外定义
    // 如需自定义SQL,可在此添加方法并在XML中实现
}

package com.apex.scaffold.office.off.infrastructure.persistence.mapper;

import com.apex.scaffold.office.off.infrastructure.persistence.po.OffEmployeePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公员工 MyBatis Mapper 接口
 *
 * <p>负责 {@code off_employee} 表的持久化操作，管理从第三方办公平台同步的员工信息。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>设计说明</h2>
 * <p>表名：{@code off_employee}<br>
 * 实体：{@link OffEmployeePO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code employee_name}：员工姓名</li>
 *   <li>{@code employee_no}：员工工号</li>
 *   <li>{@code email}：电子邮箱</li>
 *   <li>{@code mobile}：手机号码</li>
 *   <li>{@code department_id}：所属部门 ID（关联 {@code off_department}）</li>
 *   <li>{@code position}：职位</li>
 *   <li>{@code external_user_id}：外部平台用户 ID（飞书/钉钉/企业微信）</li>
 *   <li>{@code platform}：来源平台（FEISHU、DINGTALK、WECOM）</li>
 *   <li>{@code status}：员工状态（在职、离职）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>飞书（Lark）：从飞书通讯录同步员工信息</li>
 *   <li>钉钉（DingTalk）：从钉钉组织架构同步员工</li>
 *   <li>企业微信（WeChat Work）：从企业微信通讯录同步</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>组织架构同步：从第三方平台同步员工信息</li>
 *   <li>员工查询：按姓名、工号、部门查询员工</li>
 *   <li>外部映射：通过 {@code external_user_id} 关联外部平台账号</li>
 *   <li>状态管理：员工入职、离职状态变更</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入员工记录</li>
 *   <li>{@code updateById(entity)} - 更新员工信息</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询员工</li>
 *   <li>{@code selectList(wrapper)} - 条件查询员工列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OffEmployeePO
 * @see BaseMapper
 */
@Mapper
public interface OffEmployeeMapper extends BaseMapper<OffEmployeePO> {}

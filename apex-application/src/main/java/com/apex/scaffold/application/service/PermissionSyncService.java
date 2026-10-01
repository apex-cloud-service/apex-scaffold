package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.PermissionSyncResult;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.domain.model.entity.PermissionEntity;
import com.apex.scaffold.domain.repository.PermissionRepository;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 权限同步应用服务。
 *
 * <p>扫描 Spring 容器中所有 Bean 的方法,提取 {@link RequirePermission} 注解标注的权限码,
 * 与数据库中的 {@code sys_permission} 表对比,返回新增和废弃的权限码清单,供管理员审核后批量更新权限表。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>扫描所有 Spring Bean 的 public 方法,提取 {@code @RequirePermission} 注解</li>
 *   <li>查询数据库中已存在的权限码</li>
 *   <li>对比代码与数据库,返回新增权限码和废弃权限码列表</li>
 *   <li>支持管理员在线查看权限差异,手动决定是否同步</li>
 * </ul>
 *
 * <h2>同步策略</h2>
 * <ul>
 *   <li><strong>新增权限</strong>:代码中存在但数据库中不存在的权限码</li>
 *   <li><strong>废弃权限</strong>:数据库中存在但代码中不存在的权限码</li>
 *   <li><strong>不处理</strong>:本服务仅返回差异清单,不自动写库,避免误操作</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>新增接口后,手动触发权限同步,将新权限码批量写入数据库</li>
 *   <li>重构接口后,识别废弃的权限码,管理员确认后批量删除或标记</li>
 *   <li>定期审计权限表,确保代码与数据库的权限定义一致</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionSyncService {

    private final ApplicationContext applicationContext;
    private final PermissionRepository permissionRepository;

    /**
     * 扫描代码中的权限注解,与数据库对比,返回新增和废弃的权限码清单。
     *
     * <p>扫描流程:
     * <ol>
     *   <li>遍历 Spring 容器中所有 Bean</li>
     *   <li>反射获取每个 Bean 的所有 public 方法</li>
     *   <li>提取方法上的 {@link RequirePermission} 注解,收集权限码</li>
     *   <li>查询数据库中已存在的所有权限码</li>
     *   <li>计算差异:代码有但数据库无的为新增,数据库有但代码无的为废弃</li>
     * </ol>
     *
     * <p><strong>注意</strong>:本方法仅返回差异清单,不执行任何数据库写操作,
     * 由调用方(通常是管理员通过接口)决定是否批量写入或删除。
     *
     * @return 权限同步结果,包含新增列表和废弃列表
     */
    public Mono<PermissionSyncResult> syncPermissions() {
        log.info("开始扫描代码中的权限注解...");

        return Mono.fromCallable(() -> {
                    Set<String> codePermissions = scanPermissionsFromCode();
                    log.info("代码中扫描到权限码数量: {}", codePermissions.size());
                    return codePermissions;
                })
                .flatMap(codePermissions -> permissionRepository.findAll().map(dbPermissions -> {
                    Set<String> dbPermissionCodes = dbPermissions.stream()
                            .map(PermissionEntity::getCode)
                            .collect(Collectors.toSet());
                    log.info("数据库中权限码数量: {}", dbPermissionCodes.size());

                    List<String> added = codePermissions.stream()
                            .filter(code -> !dbPermissionCodes.contains(code))
                            .sorted()
                            .collect(Collectors.toList());

                    List<String> removed = dbPermissionCodes.stream()
                            .filter(code -> !codePermissions.contains(code))
                            .sorted()
                            .collect(Collectors.toList());

                    log.info("权限同步结果: 新增={}, 废弃={}", added.size(), removed.size());

                    return PermissionSyncResult.builder()
                            .addedPermissions(added)
                            .removedPermissions(removed)
                            .build();
                }));
    }

    /**
     * 扫描代码中所有 {@link RequirePermission} 注解,提取权限码。
     *
     * <p>扫描范围:Spring 容器中所有 Bean 的 public 方法。
     * 跳过 Spring 内部 Bean(包名以 {@code org.springframework} 开头)以提升性能。
     *
     * @return 权限码集合(去重)
     */
    private Set<String> scanPermissionsFromCode() {
        Set<String> permissions = new HashSet<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> clazz = bean.getClass();

                if (clazz.getName().startsWith("org.springframework")) {
                    continue;
                }

                for (Method method : clazz.getMethods()) {
                    RequirePermission annotation = method.getAnnotation(RequirePermission.class);
                    if (annotation != null) {
                        String permissionCode = annotation.value();
                        if (permissionCode != null && !permissionCode.isBlank()) {
                            permissions.add(permissionCode.trim());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("扫描 Bean 权限注解失败: beanName={}, error={}", beanName, e.getMessage());
            }
        }

        return permissions;
    }
}

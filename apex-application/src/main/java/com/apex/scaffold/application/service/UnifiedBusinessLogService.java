package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.log.UnifiedBusinessLogDTO;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.infrastructure.persistence.mapper.*;
import com.apex.scaffold.infrastructure.persistence.po.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 业务日志统一查询服务
 *
 * <p>整合多个业务日志源，提供统一查询接口。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
/**n * UnifiedBusinessLogServicen *n * <p>TODO: 补充服务的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class UnifiedBusinessLogService {

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final ApiCallLogMapper apiCallLogMapper;

    /**
     * 统一查询业务日志
     *
     * @param logType   日志类型（login/operation/api，null表示全部）
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param username  用户名（可选）
     * @param module    模块名（可选）
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 日志分页数据
     */
    public Mono<PageResponse<UnifiedBusinessLogDTO>> queryLogs(
            String logType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String username,
            String module,
            int pageNum,
            int pageSize) {

        return Mono.fromCallable(() -> {
                    List<UnifiedBusinessLogDTO> allLogs = new ArrayList<>();

                    // 查询登录日志
                    if (logType == null || "login".equals(logType)) {
                        List<UnifiedBusinessLogDTO> loginLogs = queryLoginLogs(startTime, endTime, username, pageSize);
                        allLogs.addAll(loginLogs);
                    }

                    // 查询操作日志
                    if (logType == null || "operation".equals(logType)) {
                        List<UnifiedBusinessLogDTO> operationLogs =
                                queryOperationLogs(startTime, endTime, username, module, pageSize);
                        allLogs.addAll(operationLogs);
                    }

                    // 查询API调用日志
                    if (logType == null || "api".equals(logType)) {
                        List<UnifiedBusinessLogDTO> apiLogs = queryApiLogs(startTime, endTime, username, pageSize);
                        allLogs.addAll(apiLogs);
                    }

                    // 按时间倒序排序
                    allLogs.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

                    // 分页
                    int start = (pageNum - 1) * pageSize;
                    int end = Math.min(start + pageSize, allLogs.size());
                    List<UnifiedBusinessLogDTO> pagedLogs =
                            start < allLogs.size() ? allLogs.subList(start, end) : new ArrayList<>();

                    return PageResponse.of(pagedLogs, (long) allLogs.size(), pageNum, pageSize);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Query unified business logs failed", e))
                .onErrorReturn(PageResponse.of(new ArrayList<>(), 0L, pageNum, pageSize));
    }

    private List<UnifiedBusinessLogDTO> queryLoginLogs(
            LocalDateTime startTime, LocalDateTime endTime, String username, int limit) {

        LambdaQueryWrapper<LoginLogPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(LoginLogPO::getLoginTime, startTime)
                .le(LoginLogPO::getLoginTime, endTime)
                .orderByDesc(LoginLogPO::getLoginTime)
                .last("LIMIT " + limit);

        if (username != null && !username.isEmpty()) {
            wrapper.like(LoginLogPO::getUsername, username);
        }

        List<LoginLogPO> logs = loginLogMapper.selectList(wrapper);

        return logs.stream()
                .map(log -> UnifiedBusinessLogDTO.builder()
                        .id(log.getId().toString())
                        .logType("login")
                        .timestamp(log.getLoginTime())
                        .username(log.getUsername())
                        .module("用户登录")
                        .operation(log.getStatus() == 1 ? "登录成功" : "登录失败")
                        .ipAddress(log.getIp())
                        .browser(log.getBrowser())
                        .operatingSystem(log.getOs())
                        .message(log.getMessage())
                        .build())
                .collect(Collectors.toList());
    }

    private List<UnifiedBusinessLogDTO> queryOperationLogs(
            LocalDateTime startTime, LocalDateTime endTime, String username, String module, int limit) {

        LambdaQueryWrapper<OperationLogPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(OperationLogPO::getCreateTime, startTime)
                .le(OperationLogPO::getCreateTime, endTime)
                .orderByDesc(OperationLogPO::getCreateTime)
                .last("LIMIT " + limit);

        if (username != null && !username.isEmpty()) {
            wrapper.like(OperationLogPO::getUserName, username);
        }

        if (module != null && !module.isEmpty()) {
            wrapper.like(OperationLogPO::getModule, module);
        }

        List<OperationLogPO> logs = operationLogMapper.selectList(wrapper);

        return logs.stream()
                .map(log -> UnifiedBusinessLogDTO.builder()
                        .id(log.getId().toString())
                        .logType("operation")
                        .timestamp(log.getCreateTime())
                        .username(log.getUserName())
                        .module(log.getModule())
                        .operation(log.getDescription())
                        .ipAddress(log.getIp())
                        .duration(null)
                        .status(log.getStatus())
                        .message(log.getErrorMsg())
                        .build())
                .collect(Collectors.toList());
    }

    private List<UnifiedBusinessLogDTO> queryApiLogs(
            LocalDateTime startTime, LocalDateTime endTime, String username, int limit) {

        LambdaQueryWrapper<ApiCallLogPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(ApiCallLogPO::getCallTime, startTime)
                .le(ApiCallLogPO::getCallTime, endTime)
                .orderByDesc(ApiCallLogPO::getCallTime)
                .last("LIMIT " + limit);

        List<ApiCallLogPO> logs = apiCallLogMapper.selectList(wrapper);

        return logs.stream()
                .map(log -> UnifiedBusinessLogDTO.builder()
                        .id(log.getId().toString())
                        .logType("api")
                        .timestamp(log.getCallTime())
                        .username("")
                        .module("API调用")
                        .operation(log.getMethod() + " " + log.getPath())
                        .ipAddress("")
                        .duration(log.getExecutionTime())
                        .status(log.getStatus())
                        .message(log.getErrorMsg())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 统计业务日志数量
     *
     * @param logType   日志类型
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 日志数量
     */
    public Mono<Long> countLogs(String logType, LocalDateTime startTime, LocalDateTime endTime) {

        return Mono.fromCallable(() -> {
                    long total = 0;

                    if (logType == null || "login".equals(logType)) {
                        LambdaQueryWrapper<LoginLogPO> wrapper = new LambdaQueryWrapper<>();
                        wrapper.ge(LoginLogPO::getLoginTime, startTime).le(LoginLogPO::getLoginTime, endTime);
                        total += loginLogMapper.selectCount(wrapper);
                    }

                    if (logType == null || "operation".equals(logType)) {
                        LambdaQueryWrapper<OperationLogPO> wrapper = new LambdaQueryWrapper<>();
                        wrapper.ge(OperationLogPO::getCreateTime, startTime).le(OperationLogPO::getCreateTime, endTime);
                        total += operationLogMapper.selectCount(wrapper);
                    }

                    if (logType == null || "api".equals(logType)) {
                        LambdaQueryWrapper<ApiCallLogPO> wrapper = new LambdaQueryWrapper<>();
                        wrapper.ge(ApiCallLogPO::getCallTime, startTime).le(ApiCallLogPO::getCallTime, endTime);
                        total += apiCallLogMapper.selectCount(wrapper);
                    }

                    return total;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Count unified business logs failed", e))
                .onErrorReturn(0L);
    }
}

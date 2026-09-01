package com.apex.example.handler;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import com.apex.example.dto.ExampleUserExportDTO;
import com.apex.example.service.ExampleCapabilityService;
import com.apex.scaffold.common.annotation.IdempotentApi;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.infrastructure.excel.ExcelExportService;
import com.apex.scaffold.interfaces.handler.BaseHandler;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 示例能力处理器
 *
 * <p>以「能力地图」方式集中演示脚手架核心通用能力，每个端点对应一类
 * 开箱即用能力，便于新用户快速理解与复用：
 * <ul>
 *   <li>{@code GET /api/example/capabilities/cache/{key}} - 多级缓存读穿（含击穿合并）</li>
 *   <li>{@code DELETE /api/example/capabilities/cache/{key}} - 缓存主动失效</li>
 *   <li>{@code POST /api/example/capabilities/idempotent} - 写接口幂等（{@link IdempotentApi}）</li>
 *   <li>{@code POST /api/example/capabilities/audit} - 操作审计（{@link OperationLog}）</li>
 *   <li>{@code GET /api/example/capabilities/stream} - SSE 服务端推送</li>
 *   <li>{@code GET /api/example/capabilities/mask} - 敏感数据脱敏</li>
 *   <li>{@code GET /api/example/capabilities/export/users} - Excel 导出</li>
 * </ul>
 *
 * <p>异常处理约定与 {@link ExampleUserHandler} 一致：处理器内不捕获异常，
 * 统一由全局异常处理器转换为标准 {@link Result} 错误响应。</p>
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.1.4
 */
@Slf4j
@Component
@Tag(name = "示例模块-能力演示", description = "脚手架核心能力开箱即用演示")
public class ExampleCapabilityHandler extends BaseHandler {

    /** xlsx 附件响应类型。 */
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** 能力演示服务。 */
    private final ExampleCapabilityService capabilityService;

    /** 脚手架 Excel 导出服务（EasyExcel 封装）。 */
    private final ExcelExportService excelExportService;

    /**
     * 构造能力演示处理器。
     *
     * @param messageUtils       国际化消息工具
     * @param validator          Jakarta Bean Validation 校验器
     * @param capabilityService  能力演示服务
     * @param excelExportService Excel 导出服务
     */
    public ExampleCapabilityHandler(
            MessageUtils messageUtils,
            Validator validator,
            ExampleCapabilityService capabilityService,
            ExcelExportService excelExportService) {
        super(messageUtils, validator);
        this.capabilityService = capabilityService;
        this.excelExportService = excelExportService;
    }

    /**
     * 读取缓存值（缓存读穿演示）。
     *
     * @param request HTTP 请求（路径参数 key 为业务键）
     * @return 缓存值 + 底层加载次数
     */
    @Operation(summary = "缓存读穿演示", description = "多级缓存 getWithLoader：首次加载回填，后续命中缓存")
    public Mono<ServerResponse> getCacheValue(ServerRequest request) {
        String key = request.pathVariable("key");
        return capabilityService.getCachedValue(key).flatMap(value -> ok().bodyValue(Result.success(value)));
    }

    /**
     * 缓存主动失效。
     *
     * @param request HTTP 请求（路径参数 key 为业务键）
     * @return 删除的缓存键数量
     */
    @Operation(summary = "缓存失效演示", description = "主动删除指定缓存键")
    public Mono<ServerResponse> evictCache(ServerRequest request) {
        String key = request.pathVariable("key");
        return capabilityService.evictCache(key).flatMap(count -> ok().bodyValue(
                        Result.success(Map.of("deleted", count))));
    }

    /**
     * 写接口幂等演示。
     *
     * <p>{@link IdempotentApi} 基于 Redis SETNX 去重：携带相同
     * {@code Idempotency-Key} 请求头的重复请求在 TTL 窗口内被拒绝，
     * 业务方法只执行一次。用于防重复下单/重复扣款等副作用。</p>
     *
     * @param request HTTP 请求（需携带 Idempotency-Key 请求头）
     * @return 幂等处理结果（含请求头中的幂等键）
     */
    @Operation(summary = "写接口幂等演示", description = "@IdempotentApi：相同 Idempotency-Key 窗口内重复请求被拒绝")
    @IdempotentApi(header = "Idempotency-Key", expireTime = 60)
    public Mono<ServerResponse> idempotentDemo(ServerRequest request) {
        String idempotencyKey = request.headers().firstHeader("Idempotency-Key");
        Map<String, Object> result = Map.of(
                "idempotencyKey",
                idempotencyKey == null ? "" : idempotencyKey,
                "processedAt",
                LocalDateTime.now().toString(),
                "message",
                "请求已处理（首次）");
        return ok().bodyValue(Result.success(result));
    }

    /**
     * 操作审计演示。
     *
     * <p>{@link OperationLog} 注解由切面自动记录操作人、模块、动作、
     * 请求/响应摘要至 {@code sys_operation_log} 表，支撑等保审计回溯。</p>
     *
     * @param request HTTP 请求
     * @return 审计记录结果
     */
    @Operation(summary = "操作审计演示", description = "@OperationLog：写操作自动落审计日志（sys_operation_log）")
    @OperationLog(module = "示例模块", type = OperationLog.OperationType.INSERT, description = "能力演示-审计写入")
    public Mono<ServerResponse> auditDemo(ServerRequest request) {
        return ok().bodyValue(Result.success(Map.of("audited", true, "message", "本次写操作已记录审计日志")));
    }

    /**
     * SSE 服务端推送演示。
     *
     * <p>返回 {@code text/event-stream} 流式响应，每秒推送一条心跳，
     * 共 10 条后自动结束。演示 WebFlux 响应式推送与前端
     * {@code EventSource} 的对接方式。</p>
     *
     * @param request HTTP 请求
     * @return SSE 事件流
     */
    @Operation(summary = "SSE 实时推送演示", description = "服务端事件流：每秒心跳一条，共 10 条")
    public Mono<ServerResponse> streamDemo(ServerRequest request) {
        Flux<ServerSentEvent<String>> events = capabilityService
                .streamHeartbeat()
                .map(message ->
                        ServerSentEvent.builder(message).event("heartbeat").build());
        return ok().contentType(MediaType.TEXT_EVENT_STREAM).body(events, ServerSentEvent.class);
    }

    /**
     * 敏感数据脱敏演示。
     *
     * @param request HTTP 请求（query 参数 phone/idCard/email/name）
     * @return 脱敏后的字段映射
     */
    @Operation(summary = "数据脱敏演示", description = "手机号/身份证/邮箱/姓名标准脱敏输出")
    public Mono<ServerResponse> maskDemo(ServerRequest request) {
        String phone = request.queryParam("phone").orElse("13800138000");
        String idCard = request.queryParam("idCard").orElse("110101199003071234");
        String email = request.queryParam("email").orElse("test@example.com");
        String name = request.queryParam("name").orElse("张三");
        return ok().bodyValue(Result.success(capabilityService.maskDemo(phone, idCard, email, name)));
    }

    /**
     * Excel 导出演示。
     *
     * <p>基于 EasyExcel 将内存数据导出为 xlsx 附件流，演示
     * 统一导出服务 {@link ExcelExportService} 的使用方式。</p>
     *
     * @param request HTTP 请求
     * @return xlsx 文件流（attachment 下载）
     */
    @Operation(summary = "Excel 导出演示", description = "EasyExcel 导出用户清单 xlsx 附件")
    public Mono<ServerResponse> exportUsers(ServerRequest request) {
        // 演示数据：真实场景由业务查询产出
        List<ExampleUserExportDTO> users = List.of(
                new ExampleUserExportDTO(1L, "张三", "138****8000", 0),
                new ExampleUserExportDTO(2L, "李四", "139****9000", 0),
                new ExampleUserExportDTO(3L, "王五", "137****7000", 1));
        byte[] bytes = excelExportService.export("示例用户", ExampleUserExportDTO.class, users);

        // 以附件形式返回 xlsx 字节流（byte[] 由 ByteArrayEncoder 原生编码）
        return ServerResponse.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.xlsx")
                .contentType(XLSX_MEDIA_TYPE)
                .bodyValue(bytes);
    }
}

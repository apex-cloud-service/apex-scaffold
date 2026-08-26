package com.apex.scaffold.common.util;

import com.apex.scaffold.common.result.ResultCode;
import org.springframework.http.HttpStatus;

/**
 * HTTP 状态码工具类。
 *
 * <p>提供业务错误码与 HTTP 状态码之间的映射转换功能。
 * 在全局异常处理和认证过滤器中统一使用，避免多份重复代码。
 * 直接使用 Spring 框架的 {@link HttpStatus} 枚举值，消除重复常量定义。
 *
 * <h2>映射规则</h2>
 * <table>
 *   <tr><th>错误码范围</th><th>HTTP 状态码</th><th>说明</th></tr>
 *   <tr><td>0</td><td>200 OK</td><td>成功</td></tr>
 *   <tr><td>401</td><td>401 Unauthorized</td><td>精确匹配：通用未授权</td></tr>
 *   <tr><td>40100~40199</td><td>401 Unauthorized</td>
 *       <td>范围匹配：Token 过期(40101)等认证细分错误码</td></tr>
 *   <tr><td>403</td><td>403 Forbidden</td><td>精确匹配：权限不足</td></tr>
 *   <tr><td>404</td><td>404 Not Found</td><td>精确匹配：资源不存在</td></tr>
 *   <tr><td>40400~40499</td><td>404 Not Found</td>
 *       <td>范围匹配：资源不存在的细分错误码</td></tr>
 *   <tr><td>4202</td><td>409 Conflict</td><td>对局已结算，不允许重复提交</td></tr>
 *   <tr><td>4204</td><td>404 Not Found</td><td>暂无目标性别照片（候选池枯竭）</td></tr>
 *   <tr><td>其他 400~499</td><td>400 Bad Request</td><td>通用客户端错误</td></tr>
 *   <tr><td>4600~4699</td><td>400 Bad Request</td><td>文件校验错误</td></tr>
 *   <tr><td>null 或 ≥500</td><td>500 Internal Server Error</td><td>服务器错误</td></tr>
 * </table>
 *
 * <h2>设计说明</h2>
 * <p>错误码段范围映射采用千位段约定：401xx 段统一映射为 401 Unauthorized，
 * 404xx 段统一映射为 404 Not Found。该约定已在
 * {@link com.apex.scaffold.common.result.ResultCode} 枚举中通过
 * {@code TOKEN_EXPIRED(40101)} 等值体现，本方法确保 HTTP 状态码与业务错误码
 * 的语义保持一致，避免 40101 被误映射为 400 Bad Request。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * HttpStatus status = HttpStatusUtil.fromBusinessCode(40101);
 * // 返回 HttpStatus.UNAUTHORIZED（而非 HttpStatus.BAD_REQUEST）
 * assert status == HttpStatus.UNAUTHORIZED;
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
public class HttpStatusUtil {

    /** 认证业务错误码段的宽度（含起点、不含终点）。 */
    private static final int BUSINESS_CODE_RANGE_WIDTH = 100;

    /** 扩展认证错误码段起点，覆盖 TOKEN_EXPIRED 等 401xx 错误。 */
    private static final int AUTHENTICATION_RANGE_START = ResultCode.TOKEN_EXPIRED.getCode() - 1;

    /** 扩展认证错误码段终点（不含）。 */
    private static final int AUTHENTICATION_RANGE_END = AUTHENTICATION_RANGE_START + BUSINESS_CODE_RANGE_WIDTH;

    /** 扩展资源错误码段起点，覆盖 404xx 资源错误。 */
    private static final int RESOURCE_RANGE_START = ResultCode.NOT_FOUND.getCode() * BUSINESS_CODE_RANGE_WIDTH;

    /** 扩展资源错误码段终点（不含）。 */
    private static final int RESOURCE_RANGE_END = RESOURCE_RANGE_START + BUSINESS_CODE_RANGE_WIDTH;

    /** 文件校验业务错误码段起点。 */
    private static final int FILE_VALIDATION_RANGE_START = ResultCode.FILE_TYPE_UNSUPPORTED.getCode() - 1;

    /** 文件校验业务错误码段终点（不含）。 */
    private static final int FILE_VALIDATION_RANGE_END = FILE_VALIDATION_RANGE_START + BUSINESS_CODE_RANGE_WIDTH;

    /** 私有构造函数，防止实例化 */
    private HttpStatusUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 根据业务错误码映射 HTTP 状态码。
     *
     * <p>v1.2.0 起增加错误码段范围映射，解决 {@code TOKEN_EXPIRED}(40101)
     * 等细分错误码被误映射为 {@link HttpStatus#INTERNAL_SERVER_ERROR}(500) 的问题。
     * 采用千位段约定：401xx 段 → 401 Unauthorized，404xx 段 → 404 Not Found。</p>
     *
     * <h3>判断顺序</h3>
     * <ol>
     *   <li>null → 500</li>
     *   <li>0 → 200</li>
     *   <li>BATTLE_ALREADY_SUBMITTED(4202) / BATTLE_NOT_CURRENT(4205) → 409 Conflict</li>
     *   <li>NO_TARGET_GENDER_PHOTO(4204) → 404 Not Found</li>
     *   <li>标准 HTTP 4xx 范围（400~499）→ 精确匹配 401/403/404，其余 → 400</li>
     *   <li>扩展业务错误码段（40100~40199）→ 401 Unauthorized</li>
     *   <li>扩展业务错误码段（40400~40499）→ 404 Not Found</li>
     *   <li>文件校验段（4600~4699）→ 400 Bad Request</li>
     *   <li>其余 → 500 Internal Server Error</li>
     * </ol>
     *
     * @param code 业务错误码，0表示成功
     * @return 对应的 HTTP 状态码，null 时返回 500
     * @since 1.0.0
     */
    public static HttpStatus fromBusinessCode(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (code == 0) {
            return HttpStatus.OK;
        }
        if (ResultCode.BATTLE_NOT_CURRENT.getCode().equals(code)
                || ResultCode.BATTLE_ALREADY_SUBMITTED.getCode().equals(code)
                || ResultCode.RECOMMENDATION_FEEDBACK_CONFLICT.getCode().equals(code)
                || ResultCode.ACCOUNT_DELETION_MEMBERSHIP_ACTIVE.getCode().equals(code)
                || ResultCode.ACCOUNT_DELETION_ALREADY_COMPLETED.getCode().equals(code)) {
            return HttpStatus.CONFLICT;
        }
        if (ResultCode.NO_TARGET_GENDER_PHOTO.getCode().equals(code)
                || ResultCode.NO_SINGLE_RECOMMENDATION.getCode().equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (ResultCode.PIN_DAILY_LIMIT_REACHED.getCode().equals(code)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        // 标准 HTTP 4xx 范围（400~499）：精确匹配或回落为 400
        if (code >= HttpStatus.BAD_REQUEST.value() && code < HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            if (code == HttpStatus.UNAUTHORIZED.value()) {
                return HttpStatus.UNAUTHORIZED;
            }
            if (code == HttpStatus.FORBIDDEN.value()) {
                return HttpStatus.FORBIDDEN;
            }
            if (code == HttpStatus.NOT_FOUND.value()) {
                return HttpStatus.NOT_FOUND;
            }
            return HttpStatus.BAD_REQUEST;
        }

        /*
         * 扩展业务错误码段（千位段约定）。
         * 错误码值超出标准 HTTP 4xx 范围（如 40101 > 499），
         * 需在标准范围判断之后独立处理：
         * 40100~40199 → 401 Unauthorized（覆盖 TOKEN_EXPIRED(40101) 等）
         * 40400~40499 → 404 Not Found（资源不存在的细分错误码）
         */
        if (code >= AUTHENTICATION_RANGE_START && code < AUTHENTICATION_RANGE_END) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code >= RESOURCE_RANGE_START && code < RESOURCE_RANGE_END) {
            return HttpStatus.NOT_FOUND;
        }

        // 文件校验业务码段
        if (code >= FILE_VALIDATION_RANGE_START && code < FILE_VALIDATION_RANGE_END) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

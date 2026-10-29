package com.apex.scaffold.common.util;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 工具类
 *
 * <p>基于 Jackson 实现统一的 JSON 序列化与反序列化能力，所有方法对入参进行非空校验，
 * 异常通过 {@link BusinessException} 统一包装，便于上层捕获处理。
 *
 * <p><strong>设计说明</strong></p>
 * <ul>
 *   <li>默认 <b>不启用</b> 全局 default typing，保证序列化产物为标准 JSON（无 {@code ["type", value]} 包装），
 *       避免对前端协议、跨语言交互、缓存可读性造成破坏；</li>
 *   <li>对需要多态反序列化的场景，调用方应通过字段级 {@code @JsonTypeInfo} 显式声明，
 *       或使用本工具提供的 {@link #secureFromJson(String, Class, Map)} 在白名单约束下完成多态解析；</li>
 *   <li>启用 Java 8 时间类型支持（{@code JavaTimeModule}），日期以 ISO-8601 字符串格式输出；</li>
 *   <li>反序列化时忽略未知字段，降低与协议演进的耦合；</li>
 *   <li>序列化空 Bean 不抛异常，便于处理可选字段场景。</li>
 * </ul>
 *
 * <p><strong>线程安全</strong></p>
 * <p>{@link ObjectMapper} 在初始化后即为线程安全，本工具类以静态方式对外提供，
 * 可在多线程环境中共享使用；不允许在运行期修改其配置。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@Slf4j
public final class JsonUtil {

    /** JSON 字面量 null，用于 null 入参的序列化输出。 */
    private static final String NULL_JSON = "null";

    /** 内部共享的 {@link ObjectMapper} 实例，初始化后配置不可变。 */
    private static final ObjectMapper OBJECT_MAPPER;

    /** 多态类型校验器，仅允许 {@link Object} 及其子类型在显式调用时通过，默认序列化链不使用。 */
    private static final PolymorphicTypeValidator POLYMORPHIC_TYPE_VALIDATOR;

    static {
        OBJECT_MAPPER = new ObjectMapper();

        // 安全配置：仅构造校验器，不激活全局 default typing，保证标准 JSON 输出
        POLYMORPHIC_TYPE_VALIDATOR = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        // 序列化：空 Bean 不抛异常
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 反序列化：未知字段忽略，提升协议演进兼容性
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 启用 Java 8 时间类型支持
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        // 日期以 ISO-8601 字符串输出，避免时间戳可读性差
        OBJECT_MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * 私有构造函数，禁止实例化。
     *
     * @throws UnsupportedOperationException 始终抛出，工具类不应被实例化
     */
    private JsonUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 将对象序列化为标准 JSON 字符串。
     *
     * <p>对 {@code null} 入参直接返回字符串 {@code "null"}；
     * 其他对象交由 {@link ObjectMapper} 序列化。
     * 输出为标准 JSON，不会出现多态类型包装。
     *
     * @param obj 待序列化的对象，可为 {@code null}
     * @return JSON 字符串；当 {@code obj} 为 {@code null} 时返回 {@code "null"}
     * @throws BusinessException 当序列化过程发生 I/O 或映射异常时抛出
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return NULL_JSON;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: type={}", obj.getClass().getName(), e);
            throw new BusinessException(ResultCode.JSON_ERROR, "JSON 序列化失败", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的对象。
     *
     * <p>使用标准反序列化路径，不启用多态类型解析；
     * 如需多态支持请使用 {@link #secureFromJson(String, Class, Map)}。
     *
     * @param <T>  目标对象类型
     * @param json JSON 字符串，不能为 {@code null}
     * @param clazz 目标类型，不能为 {@code null}
     * @return 反序列化后的对象
     * @throws IllegalArgumentException 当 {@code json} 或 {@code clazz} 为 {@code null}
     * @throws BusinessException 当 JSON 解析失败时抛出
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null) {
            throw new IllegalArgumentException("JSON 字符串不能为 null");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("目标类型不能为 null");
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: target={}, json={}", clazz.getName(), json, e);
            throw new BusinessException(ResultCode.JSON_ERROR, "JSON 反序列化失败", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为泛型对象（如 {@code List<User>}、{@code Map<String, Object>}）。
     *
     * <p>示例：
     * <pre>{@code
     *   List<User> users = JsonUtil.fromJson(json, new TypeReference<List<User>>() {});
     * }</pre>
     *
     * @param <T>           目标对象类型
     * @param json          JSON 字符串，不能为 {@code null}
     * @param typeReference 泛型类型引用，不能为 {@code null}
     * @return 反序列化后的对象
     * @throws IllegalArgumentException 当 {@code json} 或 {@code typeReference} 为 {@code null}
     * @throws BusinessException 当 JSON 解析失败时抛出
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null) {
            throw new IllegalArgumentException("JSON 字符串不能为 null");
        }
        if (typeReference == null) {
            throw new IllegalArgumentException("类型引用不能为 null");
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: type={}, json={}", typeReference.getType(), json, e);
            throw new BusinessException(ResultCode.JSON_ERROR, "JSON 反序列化失败", e);
        }
    }

    /**
     * 在白名单约束下将 JSON 字符串多态反序列化为指定基类的子类实例。
     *
     * <p>调用方通过 {@code allowedSubtypes} 显式声明允许实例化的子类型，
     * 用于替代已废弃的全局 default typing 机制，避免多态注入攻击（如 CVE-2019-14540）。
     *
     * <p><strong>使用示例</strong></p>
     * <pre>{@code
     *   Map<String, Class<? extends Animal>> allowed = new HashMap<>();
     *   allowed.put("dog", Dog.class);
     *   allowed.put("cat", Cat.class);
     *   Animal animal = JsonUtil.secureFromJson(json, Animal.class, allowed);
     * }</pre>
     *
     * @param <T>             基类类型
     * @param json            JSON 字符串，不能为 {@code null}
     * @param baseType        基类，不能为 {@code null}
     * @param allowedSubtypes 允许的子类型映射，键为类型标识，值为子类 Class；为空表示不允许任何多态
     * @return 反序列化后的对象
     * @throws IllegalArgumentException 当 {@code json} 或 {@code baseType} 为 {@code null}
     * @throws BusinessException 当 JSON 解析失败、或类型不在白名单时抛出
     */
    public static <T> T secureFromJson(
            String json, Class<T> baseType, Map<String, Class<? extends T>> allowedSubtypes) {
        if (json == null) {
            throw new IllegalArgumentException("JSON 字符串不能为 null");
        }
        if (baseType == null) {
            throw new IllegalArgumentException("基类不能为 null");
        }
        Map<String, Class<? extends T>> safeSubtypes =
                (allowedSubtypes == null) ? Collections.emptyMap() : allowedSubtypes;
        try {
            // 以 Map 形式解析，便于提取类型标识字段；这里复用标准 ObjectMapper
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) OBJECT_MAPPER.readValue(json, Map.class);
            String typeKey = (raw == null) ? null : (String) raw.get("@type");
            Class<? extends T> impl = (typeKey == null) ? null : safeSubtypes.get(typeKey);
            if (impl == null) {
                throw new IllegalArgumentException("未注册的多态类型: " + typeKey);
            }
            return OBJECT_MAPPER.readValue(json, impl);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.JSON_ERROR, "JSON 反序列化失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("安全多态反序列化失败: base={}, json={}", baseType.getName(), json, e);
            throw new BusinessException(ResultCode.JSON_ERROR, "JSON 反序列化失败", e);
        }
    }

    /**
     * 获取共享的 {@link ObjectMapper} 实例。
     *
     * <p>返回的实例为静态共享，调用方 <b>不应</b> 修改其配置；
     * 如需自定义 ObjectMapper，请新建实例。
     *
     * @return 共享的 {@link ObjectMapper} 实例
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}

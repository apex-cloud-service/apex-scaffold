package com.apex.scaffold.common.masking;

import com.apex.scaffold.common.annotation.DataMask;
import com.apex.scaffold.common.annotation.MaskType;
import com.apex.scaffold.common.util.DataMaskUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import java.io.IOException;

/**
 * 按属性创建不可变脱敏序列化器，适用于任意启用 Jackson 注解的 ObjectMapper。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class DataMaskSerializer extends JsonSerializer<Object> implements ContextualSerializer {
    private final MaskType type;
    private final String customRule;

    /** Jackson 使用的默认构造器。 */
    public DataMaskSerializer() {
        this(MaskType.GENERAL, "");
    }

    /**
     * 输出脱敏值，非字符串拒绝输出以避免误标注泄漏。
     * @param value 原值
     * @param generator 输出器
     * @param provider 上下文
     * @throws IOException 输出失败
     */
    @Override
    public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        if (!(value instanceof String text)) {
            throw JsonMappingException.from(generator, "DataMask 仅支持字符串属性");
        }
        generator.writeString(DataMaskUtil.mask(text, type, customRule));
    }

    /**
     * 解析字段或 getter 注解并预先校验配置。
     * @param provider 上下文
     * @param property 属性
     * @return 不可变序列化器
     * @throws JsonMappingException 配置非法
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
            throws JsonMappingException {
        DataMask annotation = property == null ? null : property.getAnnotation(DataMask.class);
        if (annotation == null) {
            return this;
        }
        if (annotation.type() == MaskType.CUSTOM) {
            try {
                DataMaskUtil.parseCustomRule(annotation.customRule());
            } catch (IllegalArgumentException ignored) {
                throw JsonMappingException.from(provider, "DataMask 自定义规则非法");
            }
        }
        return new DataMaskSerializer(annotation.type(), annotation.customRule());
    }
}

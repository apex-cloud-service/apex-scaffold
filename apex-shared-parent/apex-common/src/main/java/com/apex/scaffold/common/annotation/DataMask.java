package com.apex.scaffold.common.annotation;

import com.apex.scaffold.common.masking.DataMaskSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.*;

/**
 * Jackson 输出脱敏注解；不修改原对象与反序列化输入。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = DataMaskSerializer.class)
public @interface DataMask {
    /** 脱敏类型。 */
    MaskType type() default MaskType.GENERAL;

    /** 自定义非负码点保留长度，格式为前缀,后缀。 */
    String customRule() default "";
}

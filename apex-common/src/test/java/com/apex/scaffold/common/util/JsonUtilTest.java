package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonUtil} 单元测试
 *
 * <p>覆盖序列化、反序列化、多态安全、泛型解析、ObjectMapper 配置等关键场景。
 * 重点验证：默认 <b>不</b> 启用全局 default typing，序列化产物为标准 JSON，
 * 不出现 {@code ["type", value]} 形式的多态包装。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@DisplayName("JsonUtil 单元测试")
class JsonUtilTest {

    /** 测试用普通 Bean。 */
    static class TestUser {
        /** 用户名。 */
        private String name;
        /** 年龄。 */
        private int age;

        /**
         * 创建 {@code TestUser} 实例。
         */
        public TestUser() {}

        /**
         * 创建 {@code TestUser} 实例。
         *
         * @param name 用户名
         * @param age  年龄
         */
        public TestUser(String name, int age) {
            this.name = name;
            this.age = age;
        }

        /**
         * 获取用户名。
         *
         * @return 用户名
         */
        public String getName() {
            return name;
        }

        /**
         * 设置用户名。
         *
         * @param name 用户名
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取年龄。
         *
         * @return 年龄
         */
        public int getAge() {
            return age;
        }

        /**
         * 设置年龄。
         *
         * @param age 年龄
         */
        public void setAge(int age) {
            this.age = age;
        }
    }

    /** 仅含 name 字段的 Bean，用于未知字段测试。 */
    static class ExtraFieldsBean {
        /** 名称。 */
        private String name;

        /**
         * 创建 {@code ExtraFieldsBean} 实例。
         */
        public ExtraFieldsBean() {}

        /**
         * 获取名称。
         *
         * @return 名称
         */
        public String getName() {
            return name;
        }

        /**
         * 设置名称。
         *
         * @param name 名称
         */
        public void setName(String name) {
            this.name = name;
        }
    }

    /** 多态基类，用于 {@link JsonUtil#secureFromJson} 测试。 */
    abstract static class Animal {
        /** 名称。 */
        private String name;

        /**
         * 获取名称。
         *
         * @return 名称
         */
        public String getName() {
            return name;
        }

        /**
         * 设置名称。
         *
         * @param name 名称
         */
        public void setName(String name) {
            this.name = name;
        }
    }

    /** 多态子类：狗。 */
    static class Dog extends Animal {
        /** 品种。 */
        private String breed;

        /**
         * 创建 {@code Dog} 实例。
         */
        public Dog() {}

        /**
         * 创建 {@code Dog} 实例。
         *
         * @param name  名称
         * @param breed 品种
         */
        public Dog(String name, String breed) {
            setName(name);
            this.breed = breed;
        }

        /**
         * 获取品种。
         *
         * @return 品种
         */
        public String getBreed() {
            return breed;
        }

        /**
         * 设置品种。
         *
         * @param breed 品种
         */
        public void setBreed(String breed) {
            this.breed = breed;
        }
    }

    /** 多态子类：猫。 */
    static class Cat extends Animal {
        /** 是否室内饲养。 */
        private boolean indoor;

        /**
         * 创建 {@code Cat} 实例。
         */
        public Cat() {}

        /**
         * 创建 {@code Cat} 实例。
         *
         * @param name   名称
         * @param indoor 是否室内饲养
         */
        public Cat(String name, boolean indoor) {
            setName(name);
            this.indoor = indoor;
        }

        /**
         * 获取是否室内饲养。
         *
         * @return 是否室内饲养
         */
        public boolean isIndoor() {
            return indoor;
        }

        /**
         * 设置是否室内饲养。
         *
         * @param indoor 是否室内饲养
         */
        public void setIndoor(boolean indoor) {
            this.indoor = indoor;
        }
    }

    @Nested
    @DisplayName("toJson 序列化测试")
    class ToJsonTests {

        /**
         * 序列化普通对象应返回标准 JSON 字符串（无多态包装）。
         */
        @Test
        @DisplayName("序列化普通对象应返回标准JSON字符串（无多态包装）")
        void shouldSerializeNormalObject() {
            TestUser user = new TestUser("张三", 25);

            String json = JsonUtil.toJson(user);

            assertThat(json).isNotNull();
            assertThat(json).contains("\"name\":\"张三\"");
            assertThat(json).contains("\"age\":25");
            // 关键断言：不应出现 default typing 的 ["type",...] 包装
            assertThat(json).doesNotContain("[\"");
        }

        /**
         * 序列化 Map 应返回标准 JSON 字符串（无 {@code ["type",...]} 包装）。
         */
        @Test
        @DisplayName("序列化Map应返回标准JSON字符串（无多态包装）")
        void shouldSerializeMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("key1", "value1");
            map.put("key2", 123);

            String json = JsonUtil.toJson(map);

            assertThat(json).contains("\"key1\":\"value1\"");
            assertThat(json).contains("\"key2\":123");
            assertThat(json).startsWith("{").endsWith("}");
        }

        /**
         * 序列化 List 应返回标准 JSON 数组字符串（无多态包装）。
         */
        @Test
        @DisplayName("序列化List应返回标准JSON数组字符串（无多态包装）")
        void shouldSerializeList() {
            List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));

            String json = JsonUtil.toJson(list);

            assertThat(json).isEqualTo("[\"a\",\"b\",\"c\"]");
        }

        /**
         * 序列化 null 应返回字符串 "null"。
         */
        @Test
        @DisplayName("序列化null应返回字符串\"null\"")
        void shouldSerializeNullAsNullString() {
            String json = JsonUtil.toJson(null);

            assertThat(json).isEqualTo("null");
        }

        /**
         * 序列化基本类型应返回正确的 JSON 值。
         */
        @Test
        @DisplayName("序列化基本类型应返回正确的JSON值")
        void shouldSerializePrimitive() {
            String json = JsonUtil.toJson(42);

            assertThat(json).isEqualTo("42");
        }

        /**
         * 序列化空 Bean 应返回包含默认值的 JSON（FAIL_ON_EMPTY_BEANS=false）。
         */
        @Test
        @DisplayName("序列化空对象应返回正确JSON（FAIL_ON_EMPTY_BEANS=false）")
        void shouldSerializeEmptyBean() {
            String json = JsonUtil.toJson(new TestUser());

            assertThat(json).isNotNull();
            assertThat(json).contains("\"name\":null");
            assertThat(json).contains("\"age\":0");
        }

        /**
         * 序列化子类实例应返回 <b>无</b> 类型包装的标准 JSON（验证全局 default typing 已关闭）。
         */
        @Test
        @DisplayName("序列化子类实例应返回无类型包装的标准JSON（验证全局default typing已关闭）")
        void shouldNotEmitPolymorphicTypeWrapper() {
            Dog dog = new Dog("Buddy", "Labrador");

            String json = JsonUtil.toJson(dog);

            // 关键断言：默认序列化不得包含 ["type",...] 形式的多态包装
            assertThat(json)
                    .doesNotStartWith("[\"")
                    .startsWith("{")
                    .contains("\"name\":\"Buddy\"")
                    .contains("\"breed\":\"Labrador\"");
        }
    }

    @Nested
    @DisplayName("fromJson 反序列化测试")
    class FromJsonTests {

        /**
         * 反序列化有效 JSON 应返回正确对象。
         */
        @Test
        @DisplayName("反序列化有效JSON应返回正确对象")
        void shouldDeserializeValidJson() {
            String json = "{\"name\":\"李四\",\"age\":30}";

            TestUser user = JsonUtil.fromJson(json, TestUser.class);

            assertThat(user).isNotNull();
            assertThat(user.getName()).isEqualTo("李四");
            assertThat(user.getAge()).isEqualTo(30);
        }

        /**
         * 反序列化包含未知属性的 JSON 应成功（FAIL_ON_UNKNOWN_PROPERTIES=false）。
         */
        @Test
        @DisplayName("反序列化包含未知属性的JSON应成功（FAIL_ON_UNKNOWN_PROPERTIES=false）")
        void shouldDeserializeJsonWithUnknownProperties() {
            String json = "{\"name\":\"王五\",\"age\":35,\"extraField\":\"unknown\"}";

            TestUser user = JsonUtil.fromJson(json, TestUser.class);

            assertThat(user).isNotNull();
            assertThat(user.getName()).isEqualTo("王五");
            assertThat(user.getAge()).isEqualTo(35);
        }

        /**
         * 反序列化缺少属性的 JSON 应使用默认值。
         */
        @Test
        @DisplayName("反序列化缺少属性的JSON应使用默认值")
        void shouldDeserializeJsonWithMissingFields() {
            String json = "{\"name\":\"赵六\"}";

            TestUser user = JsonUtil.fromJson(json, TestUser.class);

            assertThat(user).isNotNull();
            assertThat(user.getName()).isEqualTo("赵六");
            assertThat(user.getAge()).isEqualTo(0);
        }

        /**
         * 反序列化 null JSON 应抛出 IllegalArgumentException。
         */
        @Test
        @DisplayName("反序列化null JSON应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullJson() {
            assertThatIllegalArgumentException().isThrownBy(() -> JsonUtil.fromJson(null, TestUser.class));
        }

        /**
         * 反序列化空字符串 JSON 应抛出 RuntimeException。
         */
        @Test
        @DisplayName("反序列化空字符串JSON应抛出RuntimeException")
        void shouldThrowExceptionForEmptyJson() {
            assertThatRuntimeException().isThrownBy(() -> JsonUtil.fromJson("", TestUser.class));
        }

        /**
         * 反序列化无效 JSON 应抛出 RuntimeException。
         */
        @Test
        @DisplayName("反序列化无效JSON应抛出RuntimeException")
        void shouldThrowExceptionForInvalidJson() {
            assertThatRuntimeException().isThrownBy(() -> JsonUtil.fromJson("{invalid json}", TestUser.class));
        }

        /**
         * 反序列化泛型 JSON（如 List）应通过 TypeReference 正确解析。
         */
        @Test
        @DisplayName("反序列化泛型JSON应通过TypeReference正确解析")
        void shouldDeserializeGenericListViaTypeReference() {
            String json = "[\"a\",\"b\",\"c\"]";

            List<String> result = JsonUtil.fromJson(json, new TypeReference<List<String>>() {});

            assertThat(result).containsExactly("a", "b", "c");
        }

        /**
         * 反序列化泛型 JSON（如 Map）应通过 TypeReference 正确解析。
         */
        @Test
        @DisplayName("反序列化泛型Map应通过TypeReference正确解析")
        void shouldDeserializeGenericMapViaTypeReference() {
            String json = "{\"k1\":\"v1\"}";

            Map<String, String> result = JsonUtil.fromJson(json, new TypeReference<Map<String, String>>() {});

            assertThat(result).containsEntry("k1", "v1");
        }
    }

    @Nested
    @DisplayName("secureFromJson 多态安全反序列化测试")
    class SecureFromJsonTests {

        /**
         * 白名单内的子类型应成功反序列化。
         */
        @Test
        @DisplayName("白名单内的子类型应成功反序列化")
        void shouldDeserializeAllowedSubtype() {
            Map<String, Class<? extends Animal>> allowed = new HashMap<>();
            allowed.put("dog", Dog.class);
            allowed.put("cat", Cat.class);
            String json = "{\"@type\":\"dog\",\"name\":\"Buddy\",\"breed\":\"Labrador\"}";

            Animal animal = JsonUtil.secureFromJson(json, Animal.class, allowed);

            assertThat(animal).isInstanceOf(Dog.class);
            assertThat(animal.getName()).isEqualTo("Buddy");
            assertThat(((Dog) animal).getBreed()).isEqualTo("Labrador");
        }

        /**
         * 未注册的多态类型应被拒绝（防止多态注入攻击）。
         */
        @Test
        @DisplayName("未注册的多态类型应被拒绝（防止多态注入攻击）")
        void shouldRejectUnregisteredSubtype() {
            Map<String, Class<? extends Animal>> allowed = new HashMap<>();
            allowed.put("dog", Dog.class);
            String maliciousJson = "{\"@type\":\"cat\",\"name\":\"Tom\",\"indoor\":true}";

            assertThatRuntimeException()
                    .isThrownBy(() -> JsonUtil.secureFromJson(maliciousJson, Animal.class, allowed))
                    .withMessageContaining("JSON 反序列化失败");
        }

        /**
         * 空白名单应拒绝所有多态类型。
         */
        @Test
        @DisplayName("空白名单应拒绝所有多态类型")
        void shouldRejectAllSubtypesWhenAllowListIsEmpty() {
            String json = "{\"@type\":\"dog\",\"name\":\"Buddy\"}";

            assertThatRuntimeException()
                    .isThrownBy(() -> JsonUtil.secureFromJson(json, Animal.class, Collections.emptyMap()))
                    .withMessageContaining("JSON 反序列化失败");
        }

        /**
         * null 入参应抛出 IllegalArgumentException。
         */
        @Test
        @DisplayName("null 入参应抛出IllegalArgumentException")
        void shouldThrowForNullJson() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> JsonUtil.secureFromJson(null, Animal.class, Collections.emptyMap()));
        }

        /**
         * null 基类应抛出 IllegalArgumentException。
         */
        @Test
        @DisplayName("null 基类应抛出IllegalArgumentException")
        void shouldThrowForNullBaseType() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> JsonUtil.secureFromJson("{}", null, Collections.emptyMap()));
        }
    }

    @Nested
    @DisplayName("getObjectMapper 获取ObjectMapper测试")
    class GetObjectMapperTests {

        /**
         * getObjectMapper 应返回非 null 的 ObjectMapper 实例。
         */
        @Test
        @DisplayName("getObjectMapper应返回非null的ObjectMapper实例")
        void shouldReturnNonNullObjectMapper() {
            ObjectMapper mapper = JsonUtil.getObjectMapper();

            assertThat(mapper).isNotNull();
        }

        /**
         * ObjectMapper 应已配置 FAIL_ON_EMPTY_BEANS 为 false。
         */
        @Test
        @DisplayName("ObjectMapper应已配置FAIL_ON_EMPTY_BEANS为false")
        void shouldHaveFailOnEmptyBeansDisabled() {
            ObjectMapper mapper = JsonUtil.getObjectMapper();

            assertThat(mapper.getSerializationConfig().isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS))
                    .isFalse();
        }

        /**
         * ObjectMapper 应已配置 FAIL_ON_UNKNOWN_PROPERTIES 为 false。
         */
        @Test
        @DisplayName("ObjectMapper应已配置FAIL_ON_UNKNOWN_PROPERTIES为false")
        void shouldHaveFailOnUnknownPropertiesDisabled() {
            ObjectMapper mapper = JsonUtil.getObjectMapper();

            assertThat(mapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                    .isFalse();
        }

        /**
         * ObjectMapper 应已配置 WRITE_DATES_AS_TIMESTAMPS 为 false。
         */
        @Test
        @DisplayName("ObjectMapper应已配置WRITE_DATES_AS_TIMESTAMPS为false")
        void shouldHaveWriteDatesAsTimestampsDisabled() {
            ObjectMapper mapper = JsonUtil.getObjectMapper();

            assertThat(mapper.getSerializationConfig().isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                    .isFalse();
        }

        /**
         * 多次调用 getObjectMapper 应返回同一实例（共享单例）。
         */
        @Test
        @DisplayName("多次调用getObjectMapper应返回同一实例")
        void shouldReturnSameInstanceOnMultipleCalls() {
            ObjectMapper mapper1 = JsonUtil.getObjectMapper();
            ObjectMapper mapper2 = JsonUtil.getObjectMapper();

            assertThat(mapper1).isSameAs(mapper2);
        }
    }

    @Nested
    @DisplayName("toJson 与 fromJson 往返测试")
    class RoundTripTests {

        /**
         * 序列化后反序列化应还原为等价对象。
         */
        @Test
        @DisplayName("序列化后反序列化应还原为等价对象")
        void shouldRoundTripCorrectly() {
            TestUser original = new TestUser("往返测试", 99);

            String json = JsonUtil.toJson(original);
            TestUser restored = JsonUtil.fromJson(json, TestUser.class);

            assertThat(restored.getName()).isEqualTo(original.getName());
            assertThat(restored.getAge()).isEqualTo(original.getAge());
        }
    }
}

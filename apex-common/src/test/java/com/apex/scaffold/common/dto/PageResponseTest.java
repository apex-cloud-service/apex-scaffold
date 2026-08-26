package com.apex.scaffold.common.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PageResponse 分页响应测试
 *
 * <p>验证 PageResponse 的 of() 工厂方法、pages/hasNext 计算逻辑、Lombok @Data 生成的方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("PageResponse 分页响应测试")
class PageResponseTest {

    @Nested
    @DisplayName("of() 工厂方法测试")
    class OfFactoryMethodTest {

        /**
         * 执行 {@code shouldReturnCorrectBasicFields} 操作。
         */
        @Test
        @DisplayName("有数据时应返回正确的 pageNum/pageSize/list/total")
        void shouldReturnCorrectBasicFields() {
            List<String> list = Arrays.asList("a", "b", "c");
            PageResponse<String> response = PageResponse.of(list, 30L, 1, 10);

            assertEquals(1, response.getPageNum());
            assertEquals(10, response.getPageSize());
            assertEquals(30L, response.getTotal());
            assertEquals(list, response.getList());
        }

        /**
         * 执行 {@code shouldCalculatePagesCorrectly} 操作。
         */
        @Test
        @DisplayName("总记录数 30，每页 10 条时，总页数应为 3")
        void shouldCalculatePagesCorrectly() {
            PageResponse<String> response = PageResponse.of(Arrays.asList("a", "b", "c"), 30L, 1, 10);

            assertEquals(3, response.getPages());
        }

        /**
         * 执行 {@code shouldRoundUpPagesCorrectly} 操作。
         */
        @Test
        @DisplayName("总记录数 25，每页 10 条时，总页数应为 3（向上取整）")
        void shouldRoundUpPagesCorrectly() {
            PageResponse<String> response = PageResponse.of(Arrays.asList("a", "b"), 25L, 1, 10);

            assertEquals(3, response.getPages());
        }

        /**
         * 执行 {@code zeroTotalShouldHaveZeroPages} 操作。
         */
        @Test
        @DisplayName("总记录数为 0 时，总页数应为 0")
        void zeroTotalShouldHaveZeroPages() {
            PageResponse<String> response = PageResponse.of(Collections.emptyList(), 0L, 1, 10);

            assertEquals(0, response.getPages());
        }

        /**
         * 转换为{@code talEqualsPageSizeShouldHaveOnePage}。
         */
        @Test
        @DisplayName("总记录数刚好等于每页大小时，总页数应为 1")
        void totalEqualsPageSizeShouldHaveOnePage() {
            PageResponse<String> response = PageResponse.of(Collections.emptyList(), 10L, 1, 10);

            assertEquals(1, response.getPages());
        }
    }

    @Nested
    @DisplayName("hasNext 计算测试")
    class HasNextCalculationTest {

        /**
         * 执行 {@code shouldHaveNextWhenPageNumLessThanPages} 操作。
         */
        @Test
        @DisplayName("当前页码小于总页数时，hasNext 应为 true")
        void shouldHaveNextWhenPageNumLessThanPages() {
            PageResponse<String> response = PageResponse.of(Arrays.asList("a", "b"), 30L, 1, 10);

            assertTrue(response.getHasNext());
        }

        /**
         * 执行 {@code shouldNotHaveNextWhenPageNumEqualsPages} 操作。
         */
        @Test
        @DisplayName("当前页码等于总页数时，hasNext 应为 false")
        void shouldNotHaveNextWhenPageNumEqualsPages() {
            PageResponse<String> response = PageResponse.of(Arrays.asList("a"), 30L, 3, 10);

            assertFalse(response.getHasNext());
        }

        /**
         * 执行 {@code shouldNotHaveNextWhenPageNumGreaterThanPages} 操作。
         */
        @Test
        @DisplayName("当前页码大于总页数时，hasNext 应为 false")
        void shouldNotHaveNextWhenPageNumGreaterThanPages() {
            PageResponse<String> response = PageResponse.of(Collections.emptyList(), 10L, 5, 10);

            assertFalse(response.getHasNext());
        }

        /**
         * 执行 {@code zeroTotalShouldNotHaveNext} 操作。
         */
        @Test
        @DisplayName("总记录数为 0 时，hasNext 应为 false")
        void zeroTotalShouldNotHaveNext() {
            PageResponse<String> response = PageResponse.of(Collections.emptyList(), 0L, 1, 10);

            assertFalse(response.getHasNext());
        }
    }

    @Nested
    @DisplayName("空数据场景测试")
    class EmptyDataTest {

        /**
         * 执行 {@code emptyListAndZeroTotalShouldWork} 操作。
         */
        @Test
        @DisplayName("空列表和 0 总记录数时应正常工作")
        void emptyListAndZeroTotalShouldWork() {
            PageResponse<String> response = PageResponse.of(Collections.emptyList(), 0L, 1, 10);

            assertTrue(response.getList().isEmpty());
            assertEquals(0L, response.getTotal());
            assertEquals(0, response.getPages());
            assertFalse(response.getHasNext());
        }

        /**
         * 执行 {@code emptyListWithNonZeroTotalShouldWork} 操作。
         */
        @Test
        @DisplayName("空列表但总记录数不为 0 时应正常计算")
        void emptyListWithNonZeroTotalShouldWork() {
            PageResponse<String> response = PageResponse.of(Collections.emptyList(), 25L, 2, 10);

            assertEquals(25L, response.getTotal());
            assertEquals(3, response.getPages());
            assertTrue(response.getHasNext());
        }
    }

    @Nested
    @DisplayName("Setter/Getter 测试")
    class SetterGetterTest {

        /**
         * 执行 {@code allPropertiesShouldBeSetAndGet} 操作。
         */
        @Test
        @DisplayName("所有属性应可通过 setter 设置，getter 获取")
        void allPropertiesShouldBeSetAndGet() {
            PageResponse<String> response = new PageResponse<>();
            List<String> list = Arrays.asList("x", "y");

            response.setList(list);
            response.setTotal(100L);
            response.setPageNum(5);
            response.setPageSize(20);
            response.setPages(5);
            response.setHasNext(false);

            assertEquals(list, response.getList());
            assertEquals(100L, response.getTotal());
            assertEquals(5, response.getPageNum());
            assertEquals(20, response.getPageSize());
            assertEquals(5, response.getPages());
            assertFalse(response.getHasNext());
        }
    }

    @Nested
    @DisplayName("Lombok equals/hashCode 测试")
    class EqualsHashCodeTest {

        /**
         * 执行 {@code equalObjectsShouldBeEqual} 操作。
         */
        @Test
        @DisplayName("属性相同的两个对象应相等")
        void equalObjectsShouldBeEqual() {
            List<String> list = Arrays.asList("a", "b");
            PageResponse<String> r1 = PageResponse.of(list, 2L, 1, 10);
            PageResponse<String> r2 = PageResponse.of(list, 2L, 1, 10);

            assertEquals(r1, r2);
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        /**
         * 执行 {@code differentTotalShouldNotBeEqual} 操作。
         */
        @Test
        @DisplayName("total 不同的两个对象应不相等")
        void differentTotalShouldNotBeEqual() {
            PageResponse<String> r1 = PageResponse.of(Collections.emptyList(), 10L, 1, 10);
            PageResponse<String> r2 = PageResponse.of(Collections.emptyList(), 20L, 1, 10);

            assertNotEquals(r1, r2);
        }
    }

    @Nested
    @DisplayName("Lombok toString 测试")
    class ToStringTest {

        /**
         * 转换为{@code stringShouldContainAllFields}。
         */
        @Test
        @DisplayName("toString 应包含 all 字段")
        void toStringShouldContainAllFields() {
            PageResponse<String> response = PageResponse.of(Arrays.asList("a"), 10L, 1, 10);
            String str = response.toString();

            assertTrue(str.contains("list"));
            assertTrue(str.contains("total"));
            assertTrue(str.contains("pageNum"));
            assertTrue(str.contains("pageSize"));
            assertTrue(str.contains("pages"));
            assertTrue(str.contains("hasNext"));
        }
    }

    @Nested
    @DisplayName("泛型测试")
    class GenericsTest {

        /**
         * 执行 {@code shouldSupportStringType} 操作。
         */
        @Test
        @DisplayName("应支持 String 类型的数据")
        void shouldSupportStringType() {
            List<String> list = Arrays.asList("hello", "world");
            PageResponse<String> response = PageResponse.of(list, 2L, 1, 10);

            assertEquals(list, response.getList());
            assertEquals("hello", response.getList().get(0));
        }

        /**
         * 执行 {@code shouldSupportIntegerType} 操作。
         */
        @Test
        @DisplayName("应支持 Integer 类型的数据")
        void shouldSupportIntegerType() {
            List<Integer> list = Arrays.asList(1, 2, 3);
            PageResponse<Integer> response = PageResponse.of(list, 3L, 1, 10);

            assertEquals(list, response.getList());
            assertEquals(1, response.getList().get(0));
        }
    }
}

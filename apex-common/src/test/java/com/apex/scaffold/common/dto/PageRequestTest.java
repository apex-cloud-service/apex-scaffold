package com.apex.scaffold.common.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PageRequest 分页请求测试
 *
 * <p>验证 PageRequest 的默认值、offset 计算、Lombok @Data 生成的方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("PageRequest 分页请求测试")
class PageRequestTest {

    @Nested
    @DisplayName("默认值测试")
    class DefaultValuesTest {

        /**
         * 执行 {@code shouldHaveCorrectDefaultValues} 操作。
         */
        @Test
        @DisplayName("无参构造函数应设置默认 pageNum=1, pageSize=10, isAsc=true")
        void shouldHaveCorrectDefaultValues() {
            PageRequest request = new PageRequest();

            assertEquals(1, request.getPageNum());
            assertEquals(10, request.getPageSize());
            assertTrue(request.getIsAsc());
            assertNull(request.getOrderBy());
        }
    }

    @Nested
    @DisplayName("offset 计算测试")
    class OffsetCalculationTest {

        /**
         * 执行 {@code offsetShouldBeZeroForFirstPage} 操作。
         */
        @Test
        @DisplayName("pageNum=1, pageSize=10 时 offset 应为 0")
        void offsetShouldBeZeroForFirstPage() {
            PageRequest request = new PageRequest();
            request.setPageNum(1);
            request.setPageSize(10);

            assertEquals(0, request.getOffset());
        }

        /**
         * 执行 {@code offsetShouldBeTenForSecondPage} 操作。
         */
        @Test
        @DisplayName("pageNum=2, pageSize=10 时 offset 应为 10")
        void offsetShouldBeTenForSecondPage() {
            PageRequest request = new PageRequest();
            request.setPageNum(2);
            request.setPageSize(10);

            assertEquals(10, request.getOffset());
        }

        /**
         * 执行 {@code offsetShouldBeFortyForThirdPageWithSize20} 操作。
         */
        @Test
        @DisplayName("pageNum=3, pageSize=20 时 offset 应为 40")
        void offsetShouldBeFortyForThirdPageWithSize20() {
            PageRequest request = new PageRequest();
            request.setPageNum(3);
            request.setPageSize(20);

            assertEquals(40, request.getOffset());
        }

        /**
         * 执行 {@code offsetShouldBeNegativeForInvalidPageNum} 操作。
         */
        @Test
        @DisplayName("pageNum=0, pageSize=10 时 offset 应为 -10")
        void offsetShouldBeNegativeForInvalidPageNum() {
            PageRequest request = new PageRequest();
            request.setPageNum(0);
            request.setPageSize(10);

            assertEquals(-10, request.getOffset());
        }

        /**
         * 执行 {@code offsetShouldBeZeroWithMinimumPageSize} 操作。
         */
        @Test
        @DisplayName("pageNum=1, pageSize=1 时 offset 应为 0")
        void offsetShouldBeZeroWithMinimumPageSize() {
            PageRequest request = new PageRequest();
            request.setPageNum(1);
            request.setPageSize(1);

            assertEquals(0, request.getOffset());
        }

        /**
         * 执行 {@code offsetShouldBeCorrectForLargeValues} 操作。
         */
        @Test
        @DisplayName("pageNum=100, pageSize=100 时 offset 应为 9900")
        void offsetShouldBeCorrectForLargeValues() {
            PageRequest request = new PageRequest();
            request.setPageNum(100);
            request.setPageSize(100);

            assertEquals(9900, request.getOffset());
        }
    }

    @Nested
    @DisplayName("Setter/Getter 测试")
    class SetterGetterTest {

        /**
         * 执行 {@code shouldSetAndGetPageNum} 操作。
         */
        @Test
        @DisplayName("setPageNum/getPageNum 应正确工作")
        void shouldSetAndGetPageNum() {
            PageRequest request = new PageRequest();
            request.setPageNum(5);

            assertEquals(5, request.getPageNum());
        }

        /**
         * 执行 {@code shouldSetAndGetPageSize} 操作。
         */
        @Test
        @DisplayName("setPageSize/getPageSize 应正确工作")
        void shouldSetAndGetPageSize() {
            PageRequest request = new PageRequest();
            request.setPageSize(50);

            assertEquals(50, request.getPageSize());
        }

        /**
         * 执行 {@code shouldSetAndGetOrderBy} 操作。
         */
        @Test
        @DisplayName("setOrderBy/getOrderBy 应正确工作")
        void shouldSetAndGetOrderBy() {
            PageRequest request = new PageRequest();
            request.setOrderBy("create_time");

            assertEquals("create_time", request.getOrderBy());
        }

        /**
         * 执行 {@code shouldSetAndGetIsAsc} 操作。
         */
        @Test
        @DisplayName("setIsAsc/getIsAsc 应正确工作")
        void shouldSetAndGetIsAsc() {
            PageRequest request = new PageRequest();
            request.setIsAsc(false);

            assertFalse(request.getIsAsc());
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
            PageRequest request1 = new PageRequest();
            request1.setPageNum(1);
            request1.setPageSize(10);

            PageRequest request2 = new PageRequest();
            request2.setPageNum(1);
            request2.setPageSize(10);

            assertEquals(request1, request2);
            assertEquals(request1.hashCode(), request2.hashCode());
        }

        /**
         * 执行 {@code differentPageNumShouldNotBeEqual} 操作。
         */
        @Test
        @DisplayName("pageNum 不同的两个对象应不相等")
        void differentPageNumShouldNotBeEqual() {
            PageRequest request1 = new PageRequest();
            request1.setPageNum(1);

            PageRequest request2 = new PageRequest();
            request2.setPageNum(2);

            assertNotEquals(request1, request2);
        }

        /**
         * 执行 {@code differentPageSizeShouldNotBeEqual} 操作。
         */
        @Test
        @DisplayName("pageSize 不同的两个对象应不相等")
        void differentPageSizeShouldNotBeEqual() {
            PageRequest request1 = new PageRequest();
            request1.setPageSize(10);

            PageRequest request2 = new PageRequest();
            request2.setPageSize(20);

            assertNotEquals(request1, request2);
        }

        /**
         * 执行 {@code sameObjectShouldBeEqual} 操作。
         */
        @Test
        @DisplayName("相同对象的 equals 应返回 true")
        void sameObjectShouldBeEqual() {
            PageRequest request = new PageRequest();
            assertEquals(request, request);
        }

        /**
         * 执行 {@code nullComparisonShouldReturnFalse} 操作。
         */
        @Test
        @DisplayName("与 null 比较应返回 false")
        void nullComparisonShouldReturnFalse() {
            PageRequest request = new PageRequest();
            assertNotEquals(null, request);
        }

        /**
         * 执行 {@code differentTypeComparisonShouldReturnFalse} 操作。
         */
        @Test
        @DisplayName("与不同类型的对象比较应返回 false")
        void differentTypeComparisonShouldReturnFalse() {
            PageRequest request = new PageRequest();
            assertNotEquals("string", request);
        }
    }

    @Nested
    @DisplayName("Lombok toString 测试")
    class ToStringTest {

        /**
         * 转换为{@code stringShouldContainAllFields}。
         */
        @Test
        @DisplayName("toString 应包含所有字段")
        void toStringShouldContainAllFields() {
            PageRequest request = new PageRequest();
            String str = request.toString();

            assertTrue(str.contains("pageNum"));
            assertTrue(str.contains("pageSize"));
            assertTrue(str.contains("orderBy"));
            assertTrue(str.contains("isAsc"));
        }
    }

    @Nested
    @DisplayName("边缘值与边界场景测试")
    class EdgeCaseTest {

        /**
         * 执行 {@code nullOrderByShouldNotAffectOffset} 操作。
         */
        @Test
        @DisplayName("orderBy 为 null 时不应影响 offset 计算")
        void nullOrderByShouldNotAffectOffset() {
            PageRequest request = new PageRequest();
            request.setOrderBy(null);

            assertEquals(0, request.getOffset());
            assertNull(request.getOrderBy());
        }

        /**
         * 执行 {@code allCustomFieldsShouldBeConsistent} 操作。
         */
        @Test
        @DisplayName("所有字段均设置为自定义值后属性应一致")
        void allCustomFieldsShouldBeConsistent() {
            PageRequest request = new PageRequest();
            request.setPageNum(5);
            request.setPageSize(25);
            request.setOrderBy("id");
            request.setIsAsc(false);

            assertEquals(5, request.getPageNum());
            assertEquals(25, request.getPageSize());
            assertEquals("id", request.getOrderBy());
            assertFalse(request.getIsAsc());
            assertEquals(100, request.getOffset());
        }

        /**
         * 执行 {@code nullPageSizeShouldHandleGracefully} 操作。
         */
        @Test
        @DisplayName("pageSize 为 null 时不应抛出 NullPointerException")
        void nullPageSizeShouldHandleGracefully() {
            PageRequest request = new PageRequest();
            request.setPageSize(null);
            request.setPageNum(1);

            Integer offset = request.getOffset();

            assertEquals(0, offset);
        }
    }
}

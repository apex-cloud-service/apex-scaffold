package com.apex.scaffold.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

final class RepositoryTestSupport {
    static final String TENANT = "tenant-a";

    private RepositoryTestSupport() {}

    static void condition(Wrapper<?> wrapper, String column, String operator, Object value) {
        String sql = wrapper.getSqlSegment();
        Matcher matcher = Pattern.compile(
                        "(?<![a-z_])" + column + " " + Pattern.quote(operator) + " #\\{ew.paramNameValuePairs.([^}]+)}")
                .matcher(sql);
        assertThat(matcher.find())
                .as("SQL condition %s %s in %s", column, operator, sql)
                .isTrue();
        assertThat(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().get(matcher.group(1)))
                .isEqualTo(value);
    }

    static void scoped(Wrapper<?> wrapper) {
        condition(wrapper, "tenant_id", "=", TENANT);
    }

    static void entity(Publisher<?> result, String outcome, Throwable failure) {
        StepVerifier.FirstStep<?> verifier =
                StepVerifier.create(Flux.from(result).contextWrite(TenantContext.withTenant(TENANT)));
        if (outcome.equals("error")) {
            verifier.expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                    .verify(Duration.ofSeconds(5));
        } else if (outcome.equals("empty")) {
            verifier.expectComplete().verify(Duration.ofSeconds(5));
        } else {
            verifier.assertNext(value ->
                            assertThat(value).extracting("id", "tenantId").containsExactly(7L, TENANT))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));
        }
    }

    static void value(Publisher<?> result, Object expected) {
        StepVerifier.create(Flux.from(result).contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(value -> assertThat(value).isEqualTo(expected))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    static void businessError(Publisher<?> result, ResultCode code) {
        StepVerifier.create(Flux.from(result).contextWrite(TenantContext.withTenant(TENANT)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(code.getCode());
                })
                .verify(Duration.ofSeconds(5));
    }
}

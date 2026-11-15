package com.apex.scaffold.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.auth.infrastructure.persistence.converter.TokenConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.TokenMapper;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.auth.infrastructure.persistence.repository.TokenRepositoryImpl;
import io.r2dbc.spi.ConnectionFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

class TokenTransactionWiringTest {
    @Test
    void repositoryUsesJdbcManagerWhenReactiveManagerAlsoExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceTransactionManagerAutoConfiguration.class,
                        R2dbcTransactionManagerAutoConfiguration.class))
                .withBean(DataSource.class, () -> new DriverManagerDataSource("jdbc:h2:mem:auth_wiring", "sa", ""))
                .withBean(
                        ConnectionFactory.class,
                        () -> io.r2dbc.spi.ConnectionFactories.get("r2dbc:h2:mem:///auth_wiring"))
                .withBean(TokenMapper.class, () -> mock(TokenMapper.class))
                .withBean(UserMapper.class, () -> mock(UserMapper.class))
                .withBean(TokenConverter.class, () -> Mappers.getMapper(TokenConverter.class))
                .withUserConfiguration(TokenRepositoryImpl.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PlatformTransactionManager.class))
                            .isInstanceOf(DataSourceTransactionManager.class);
                    assertThat(context).hasSingleBean(TokenRepositoryImpl.class);
                    assertThat(context)
                            .hasSingleBean(org.springframework.r2dbc.connection.R2dbcTransactionManager.class);
                    assertThat(context.getBeansOfType(PlatformTransactionManager.class))
                            .hasSize(1);
                    assertThat(org.springframework.test.util.ReflectionTestUtils.getField(
                                    context.getBean(TokenRepositoryImpl.class), "dataSource"))
                            .isSameAs(context.getBean(DataSource.class));
                });
    }
}

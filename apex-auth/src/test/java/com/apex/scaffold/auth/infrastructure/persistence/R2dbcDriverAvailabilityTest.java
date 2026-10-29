package com.apex.scaffold.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class R2dbcDriverAvailabilityTest {

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql", "h2"})
    void shouldProvideSupportedDatabaseDriver(String driver) {
        var options = ConnectionFactoryOptions.parse(
                driver.equals("h2") ? "r2dbc:h2:mem:///auth" : "r2dbc:" + driver + "://localhost/auth");
        assertThat(ConnectionFactories.supports(options))
                .as("R2DBC provider for %s", driver)
                .isTrue();
    }
}

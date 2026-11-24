package com.apex.example.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.druid.DruidConfig;
import com.baomidou.dynamic.datasource.creator.druid.DruidDataSourceCreator;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

class ExampleDataSourceTimeoutTest {

    @Test
    void shouldFailUnresponsiveDatabaseHandshakeAtConfiguredSocketTimeout() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-test.yml"));
        var config = new Binder(new MapConfigurationPropertySource(yaml.getObject()))
                .bind("spring.datasource.dynamic.druid", Bindable.of(DruidConfig.class))
                .get();
        assertThat(config.getSocketTimeout()).isEqualTo(10000);
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            var executor = Executors.newSingleThreadExecutor();
            var accepted = executor.submit(server::accept);
            var watchdog = Executors.newSingleThreadScheduledExecutor();
            try {
                // 仅在超时配置回归时解除阻塞，不能替代数据源自身的超时。
                watchdog.schedule(
                        () -> {
                            try (var socket = accepted.get(1, TimeUnit.SECONDS)) {
                                socket.shutdownOutput();
                            } catch (Exception exception) {
                                throw new IllegalStateException(exception);
                            }
                        },
                        20,
                        TimeUnit.SECONDS);
                var property = new DataSourceProperty();
                property.setPoolName("example-timeout-test");
                property.setDriverClassName("com.mysql.cj.jdbc.Driver");
                property.setUrl("jdbc:mysql://127.0.0.1:" + server.getLocalPort() + "/timeout_test?useSSL=false");
                property.setLazy(true);
                var creator = new DruidDataSourceCreator(config, null);
                try (var dataSource = (DruidDataSource) creator.createDataSource(property)) {
                    assertThat(dataSource.getConnectProperties()).containsEntry("socketTimeout", "10000");
                    assertThatThrownBy(() -> {
                                try (var connection = dataSource.getConnection()) {
                                    connection.isValid(1);
                                }
                            })
                            .hasRootCauseInstanceOf(SocketTimeoutException.class);
                }
            } finally {
                watchdog.shutdownNow();
                server.close();
                if (accepted.isDone() && !accepted.isCancelled()) {
                    accepted.get(1, TimeUnit.SECONDS).close();
                }
                executor.shutdownNow();
            }
        }
    }
}

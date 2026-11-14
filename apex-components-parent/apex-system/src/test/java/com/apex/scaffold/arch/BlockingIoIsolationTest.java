package com.apex.scaffold.arch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.service.UnifiedBusinessLogService;
import com.apex.scaffold.infrastructure.config.LokiConfig.LokiProperties;
import com.apex.scaffold.infrastructure.config.MeilisearchConfig.MeilisearchProperties;
import com.apex.scaffold.infrastructure.log.LokiAdapter;
import com.apex.scaffold.infrastructure.persistence.mapper.*;
import com.apex.scaffold.infrastructure.persistence.po.ConfigPO;
import com.apex.scaffold.infrastructure.search.MeilisearchAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 验证请求线程订阅时，同步访问在所属专用池执行。 */
class BlockingIoIsolationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void businessLogQueriesUseJdbcPool() {
        LoginLogMapper login = mock(LoginLogMapper.class);
        OperationLogMapper operation = mock(OperationLogMapper.class);
        ApiCallLogMapper api = mock(ApiCallLogMapper.class);
        when(login.selectList(any())).thenAnswer(call -> {
            assertPool("jdbc");
            return List.of();
        });
        when(operation.selectList(any())).thenAnswer(call -> {
            assertPool("jdbc");
            return List.of();
        });
        when(api.selectList(any())).thenAnswer(call -> {
            assertPool("jdbc");
            return List.of();
        });
        when(login.selectCount(any())).thenAnswer(call -> {
            assertPool("jdbc");
            return 1L;
        });
        when(operation.selectCount(any())).thenAnswer(call -> {
            assertPool("jdbc");
            return 2L;
        });
        when(api.selectCount(any())).thenAnswer(call -> {
            assertPool("jdbc");
            return 3L;
        });
        UnifiedBusinessLogService service = new UnifiedBusinessLogService(login, operation, api);
        LocalDateTime now = LocalDateTime.now();
        var query = service.queryLogs(null, now.minusHours(1), now, null, null, 1, 10);
        var count = service.countLogs(null, now.minusHours(1), now);
        verifyNoInteractions(login, operation, api);
        assertNotNull(await(query));
        assertEquals(6L, await(count));
        verify(login).selectList(any());
        verify(operation).selectList(any());
        verify(api).selectList(any());
    }

    @Test
    void lokiSynchronousBridgeUsesRemotePool() {
        AtomicInteger requests = new AtomicInteger();
        WebClient.Builder builder = WebClient.builder()
                .defaultUriVariables(java.util.Map.of("job=\"apex-scaffold\"", "test"))
                .exchangeFunction(request -> {
                    assertPool("remote");
                    requests.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("{\"data\":{\"result\":[]}}")
                            .build());
                });
        LokiAdapter adapter = new LokiAdapter(builder, new LokiProperties(), new ObjectMapper());
        LocalDateTime now = LocalDateTime.now();
        var query = adapter.queryLogs(now.minusHours(1), now, null, null, null, 10);
        var count = adapter.countLogs(now.minusHours(1), now, null);
        assertEquals(0, requests.get());
        assertTrue(await(query).isEmpty());
        assertEquals(0L, await(count));
        assertEquals(2, requests.get());
    }

    @Test
    void meilisearchSeparatesJdbcAndRemoteIncludingBatchOrchestration() throws Exception {
        Client client = mock(Client.class);
        Index index = mock(Index.class);
        ConfigMapper config = mock(ConfigMapper.class);
        MeilisearchProperties properties = spy(new MeilisearchProperties());
        when(properties.getBatchSize()).thenAnswer(call -> {
            assertPool("general");
            return 100;
        });
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new org.apache.ibatis.session.Configuration(), "test"),
                ConfigPO.class);
        ConfigPO document = new ConfigPO();
        document.setId(1L);
        document.setConfigType(1);
        when(config.selectById(1L)).thenAnswer(call -> {
            assertPool("jdbc");
            return document;
        });
        when(config.selectList(any())).thenAnswer(call -> {
            assertPool("jdbc");
            return List.of(document);
        });
        when(client.index(anyString())).thenAnswer(call -> {
            assertPool("remote");
            return index;
        });
        when(index.addDocuments(anyString())).thenAnswer(call -> {
            assertPool("remote");
            return null;
        });
        MeilisearchAdapter adapter = new MeilisearchAdapter(
                client,
                properties,
                new ObjectMapper(),
                mock(UserMapper.class),
                mock(RoleMapper.class),
                mock(MenuMapper.class),
                mock(DeptMapper.class),
                mock(DictDataMapper.class),
                config);
        var single = adapter.indexResource("config", "1");
        var batch = adapter.indexResourceBatch("config", List.of("1"));
        var rebuild = adapter.rebuildIndex("config");
        var incremental = adapter.syncIncremental("config", 0L);
        verifyNoInteractions(config, client, index);
        assertTrue(await(single));
        assertEquals(1, await(batch));
        assertEquals(1L, await(rebuild));
        assertEquals(1L, await(incremental));
        verify(index, times(4)).addDocuments(anyString());
        assertFalse(await(adapter.indexResource("unknown", "1")));
        assertEquals(0, await(adapter.indexResourceBatch("unknown", List.of("1"))));
        verify(index, times(4)).addDocuments(anyString());
    }

    @Test
    void meilisearchSearchAndDeleteUseRemotePool() throws Exception {
        Client client = mock(Client.class);
        Index index = mock(Index.class);
        when(client.index(anyString())).thenAnswer(call -> {
            assertPool("remote");
            return index;
        });
        when(index.search(any(SearchRequest.class))).thenAnswer(call -> {
            assertPool("remote");
            throw new IllegalStateException("test remote failure");
        });
        when(index.deleteDocument(anyString())).thenAnswer(call -> {
            assertPool("remote");
            return null;
        });
        MeilisearchAdapter adapter = new MeilisearchAdapter(
                client,
                new MeilisearchProperties(),
                new ObjectMapper(),
                mock(UserMapper.class),
                mock(RoleMapper.class),
                mock(MenuMapper.class),
                mock(DeptMapper.class),
                mock(DictDataMapper.class),
                mock(ConfigMapper.class));
        var search = adapter.search("test", null, null, null, 1, 10);
        var delete = adapter.removeIndex("config", "1");
        verifyNoInteractions(client, index);
        assertNotNull(await(search));
        assertTrue(await(delete));
        verify(index).search(any(SearchRequest.class));
        verify(index).deleteDocument("config_1");
    }

    private static void assertPool(String pool) {
        assertTrue(
                Thread.currentThread().getName().startsWith("apex." + pool + "-"),
                () -> "Unexpected thread: " + Thread.currentThread().getName());
    }

    private static <T> T await(Mono<T> publisher) {
        return publisher.subscribeOn(Schedulers.parallel()).block(TIMEOUT);
    }
}

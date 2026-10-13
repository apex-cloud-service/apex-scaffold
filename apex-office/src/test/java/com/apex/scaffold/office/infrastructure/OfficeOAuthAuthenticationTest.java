package com.apex.scaffold.office.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.OfficePlatformApp;
import com.apex.scaffold.office.infrastructure.client.DingTalkOfficeClient;
import com.apex.scaffold.office.infrastructure.client.FeishuOfficeClient;
import com.apex.scaffold.office.infrastructure.client.WeComOfficeClient;
import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OfficeOAuthAuthenticationTest {
    @Test
    void feishuUsesPostAndBearerHeaderAndReturnsOnlyIdentity() {
        List<ClientRequest> requests = new ArrayList<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            String body = requests.size() == 1
                    ? "{\"access_token\":\"platform-secret\"}"
                    : "{\"code\":0,\"data\":{\"open_id\":\"external\"}}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        });
        FeishuOfficeClient client = new FeishuOfficeClient(builder, new OfficePlatformProperties());
        OfficePlatformApp app = OfficePlatformApp.builder()
                .platform(OfficePlatform.FEISHU)
                .appId("tenant-app")
                .secretReference("PATH")
                .redirectUri("https://callback")
                .build();
        StepVerifier.create(client.authenticate(app, "code"))
                .expectNext("external")
                .verifyComplete();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).method()).isEqualTo(HttpMethod.POST);
        assertThat(requests.get(0).url().getQuery()).isNull();
        assertThat(requests.get(1).headers().getFirst("Authorization")).isEqualTo("Bearer platform-secret");
        assertThat(requests.get(1).url().toString()).doesNotContain("platform-secret");
    }

    @Test
    void dingTalkUsesPostTokenEndpointAndAccessTokenHeader() {
        List<ClientRequest> requests = new ArrayList<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            String body = requests.size() == 1 ? "{\"accessToken\":\"ding-token\"}" : "{\"unionId\":\"ding-user\"}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        });
        DingTalkOfficeClient client = new DingTalkOfficeClient(builder, new OfficePlatformProperties());
        OfficePlatformApp app = app(OfficePlatform.DINGTALK);
        StepVerifier.create(client.authenticate(app, "code"))
                .expectNext("ding-user")
                .verifyComplete();
        assertThat(requests.get(0).method()).isEqualTo(HttpMethod.POST);
        assertThat(requests.get(1).headers().getFirst("x-acs-dingtalk-access-token"))
                .isEqualTo("ding-token");
    }

    @Test
    void weComUsesGetTokenThenUserInfoWithAccessTokenQuery() {
        List<ClientRequest> requests = new ArrayList<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            String body = requests.size() == 1 ? "{\"access_token\":\"we-token\"}" : "{\"UserId\":\"we-user\"}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        });
        WeComOfficeClient client = new WeComOfficeClient(builder, new OfficePlatformProperties());
        StepVerifier.create(client.authenticate(app(OfficePlatform.WECOM), "code"))
                .expectNext("we-user")
                .verifyComplete();
        assertThat(requests.get(0).method()).isEqualTo(HttpMethod.GET);
        assertThat(requests.get(1).url().getQuery()).contains("access_token=we-token");
    }

    private OfficePlatformApp app(OfficePlatform platform) {
        return OfficePlatformApp.builder()
                .platform(platform)
                .appId("tenant-app")
                .secretReference("PATH")
                .redirectUri("https://callback")
                .build();
    }
}

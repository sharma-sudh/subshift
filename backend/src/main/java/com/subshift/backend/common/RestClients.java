package com.subshift.backend.common;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** RestClient factory with timeouts, so a slow external API can't hang a request thread forever. */
public final class RestClients {

    private RestClients() {}

    public static RestClient withTimeouts(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}

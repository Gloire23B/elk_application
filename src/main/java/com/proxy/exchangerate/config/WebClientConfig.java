package com.proxy.exchangerate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Configuration du WebClient pour les appels vers l'API externe de taux de change.
 */
@Configuration
public class WebClientConfig {

    @Value("${exchange-rate.api.base-url:https://api.exchangerate-api.com/v4}")
    private String apiBaseUrl;

    @Value("${exchange-rate.api.timeout-seconds:10}")
    private int timeoutSeconds;

    @Bean
    public WebClient exchangeRateWebClient() {
        return WebClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "ExchangeRateProxy/1.0")
                .codecs(config -> config
                        .defaultCodecs()
                        .maxInMemorySize(512 * 1024)) // 512 KB max réponse
                .build();
    }
}

package com.exchangerate.proxy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration du client HTTP pour appels vers l'API externe.
 *
 * <p>Utilise {@link RestTemplateBuilder} (approche idiomatique Spring Boot 3.x)
 * qui configure en interne Apache HttpClient 5 avec les bons timeouts.
 *
 * <p>Décisions :
 * <ul>
 *   <li>Timeout connexion : 5s (fail-fast si le broker est down)</li>
 *   <li>Timeout lecture : configurable via app.exchange-rate.http-timeout-ms</li>
 * </ul>
 *
 * CORRECTION : La version précédente utilisait l'API Apache HC5 directement
 * (evictIdleConnections(long, TimeUnit)) qui a changé de signature dans HC5.
 * RestTemplateBuilder évite cette dépendance de version.
 */
@Slf4j
@Configuration
public class RestTemplateConfig {

    @Value("${app.exchange-rate.http-timeout-ms:10000}")
    private int httpTimeoutMs;

    /**
     * RestTemplate avec timeouts configurés via RestTemplateBuilder.
     * Spring Boot 3.x gère automatiquement le pool Apache HttpClient 5.
     *
     * @param builder le builder Spring Boot auto-configuré
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofMillis(httpTimeoutMs))
                .build();

        log.info("RestTemplate configuré: connectTimeout=5s, readTimeout={}ms", httpTimeoutMs);
        return restTemplate;
    }
}

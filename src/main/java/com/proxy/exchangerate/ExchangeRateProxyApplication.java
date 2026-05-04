package com.proxy.exchangerate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée principal — Exchange Rate Proxy.
 *
 * Architecture :
 *   API Externe → Scheduler → Kafka Producer → Kafka Consumer → Elasticsearch
 *                                                              ↓
 *                                             REST API → 15 000 équipes internes
 */
@SpringBootApplication
@EnableScheduling
public class ExchangeRateProxyApplication {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateProxyApplication.class);

    public static void main(String[] args) {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║   Exchange Rate Proxy — Démarrage        ║");
        log.info("║   Kafka + Elasticsearch + REST API       ║");
        log.info("╚══════════════════════════════════════════╝");
        SpringApplication.run(ExchangeRateProxyApplication.class, args);
    }
}

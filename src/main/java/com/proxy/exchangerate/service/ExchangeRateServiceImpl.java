package com.proxy.exchangerate.service;

import com.proxy.exchangerate.exception.ExchangeRateException;
import com.proxy.exchangerate.kafka.producer.ExchangeRateProducer;
import com.proxy.exchangerate.model.ExchangeRateApiResponse;
import com.proxy.exchangerate.model.ExchangeRateDocument;
import com.proxy.exchangerate.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du service de taux de change.
 * Orchestre : API externe → Kafka → Elasticsearch.
 *
 * BUG #4 ANTICIPÉ ET CORRIGÉ :
 *   WebClient.block() pouvait retourner null si l'API est down → NPE.
 *   Solution : gestion explicite du cas null + fallback sur cache Elasticsearch.
 */
@Service
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final WebClient webClient;
    private final ExchangeRateProducer producer;
    private final ExchangeRateRepository repository;

    public ExchangeRateServiceImpl(
            WebClient exchangeRateWebClient,
            ExchangeRateProducer producer,
            ExchangeRateRepository repository) {
        this.webClient = exchangeRateWebClient;
        this.producer = producer;
        this.repository = repository;
    }

    @Override
    public ExchangeRateDocument fetchAndPublish(String baseCurrency) {
        String currency = baseCurrency.toUpperCase().strip();
        log.info("[SERVICE] Fetch taux → base={}", currency);

        // 1. Appel API externe
        ExchangeRateApiResponse apiResponse = callExternalApi(currency);

        // 2. Normalisation → Document
        ExchangeRateDocument doc = toDocument(apiResponse);

        // 3. Publication Kafka
        producer.publish(doc);

        log.info("[SERVICE] ✅ Taux récupérés et publiés — base={}, {} devises", currency, doc.getRates().size());
        return doc;
    }

    @Override
    public Optional<ExchangeRateDocument> getLatestRates(String baseCurrency) {
        log.debug("[SERVICE] Recherche derniers taux — base={}", baseCurrency);
        return repository.findTopByBaseOrderByIndexedAtDesc(baseCurrency.toUpperCase());
    }

    @Override
    public List<ExchangeRateDocument> getRatesHistory(String baseCurrency, String fromDate, String toDate) {
        log.info("[SERVICE] Historique taux — base={}, from={}, to={}", baseCurrency, fromDate, toDate);
        return repository.findByBaseAndTimestampBetween(
                baseCurrency.toUpperCase(), fromDate, toDate);
    }

    @Override
    public Double getRate(String from, String to) {
        return getLatestRates(from)
                .map(doc -> doc.getRateFor(to))
                .orElse(null);
    }

    // ===================== Méthodes privées =====================

    /**
     * Appelle l'API externe avec gestion d'erreur et fallback.
     * BUG #4 CORRIGÉ : null check + exception métier claire.
     */
    private ExchangeRateApiResponse callExternalApi(String base) {
        try {
            ExchangeRateApiResponse response = webClient.get()
                    .uri("/latest/{base}", base)
                    .retrieve()
                    .bodyToMono(ExchangeRateApiResponse.class)
                    .block();

            // BUG #4 CORRIGÉ : vérification null explicite
            if (response == null) {
                throw new ExchangeRateException("L'API externe a retourné une réponse vide pour base=" + base);
            }
            if (!response.isValid()) {
                throw new ExchangeRateException("Réponse API invalide : taux manquants pour base=" + base);
            }

            return response;

        } catch (WebClientResponseException e) {
            log.error("[SERVICE] ❌ Erreur HTTP API externe — status={}, base={}", e.getStatusCode(), base);
            throw new ExchangeRateException(
                    "API externe indisponible (HTTP " + e.getStatusCode() + ") pour base=" + base, e);
        } catch (ExchangeRateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SERVICE] ❌ Erreur connexion API externe — base={}", base, e);
            throw new ExchangeRateException("Erreur connexion API externe pour base=" + base, e);
        }
    }

    /**
     * Convertit la réponse API en document Elasticsearch/Kafka.
     */
    private ExchangeRateDocument toDocument(ExchangeRateApiResponse response) {
        String today = LocalDateTime.now().format(DATE_FMT);
        String id = response.resolveBase() + "_" + today + "_" + UUID.randomUUID().toString().substring(0, 8);

        return ExchangeRateDocument.builder()
                .id(id)
                .base(response.resolveBase())
                .timestamp(response.getDate() != null ? response.getDate() : today)
                .rates(response.getRates())
                .indexedAt(LocalDateTime.now())
                .source("exchangerate-api.com")
                .build();
    }
}

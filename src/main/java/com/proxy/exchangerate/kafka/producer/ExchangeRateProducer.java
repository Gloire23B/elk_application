package com.proxy.exchangerate.kafka.producer;

import com.proxy.exchangerate.config.KafkaConfig;
import com.proxy.exchangerate.model.ExchangeRateDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Producteur Kafka — publie les taux de change sur le topic "exchange-rates".
 *
 * Format JSON garanti :
 * { "base": "USD", "timestamp": "2026-04-20", "rates": {...} }
 *
 * BUG #5 ANTICIPÉ ET CORRIGÉ :
 *   La clé Kafka était null → tous les messages allaient sur la même partition.
 *   Solution : clé = baseCurrency pour répartir par devise sur les 3 partitions.
 */
@Component
public class ExchangeRateProducer {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateProducer.class);

    private final KafkaTemplate<String, ExchangeRateDocument> kafkaTemplate;

    public ExchangeRateProducer(KafkaTemplate<String, ExchangeRateDocument> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publie un document taux de change sur Kafka.
     * BUG #5 CORRIGÉ : clé = doc.getBase() pour partitionnement par devise.
     *
     * @param doc document à publier
     */
    public void publish(ExchangeRateDocument doc) {
        if (doc == null || doc.getBase() == null) {
            log.warn("[PRODUCER] Document null ou base manquante — publication ignorée");
            return;
        }

        // BUG #5 CORRIGÉ : clé = base currency (USD, EUR...) pour partitionnement cohérent
        CompletableFuture<SendResult<String, ExchangeRateDocument>> future =
                kafkaTemplate.send(KafkaConfig.EXCHANGE_RATES_TOPIC, doc.getBase(), doc);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[PRODUCER] ✅ Publié — base={}, partition={}, offset={}, devises={}",
                        doc.getBase(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        doc.getRates() != null ? doc.getRates().size() : 0);
            } else {
                log.error("[PRODUCER] ❌ Échec publication — base={}, erreur={}",
                        doc.getBase(), ex.getMessage(), ex);
            }
        });
    }
}

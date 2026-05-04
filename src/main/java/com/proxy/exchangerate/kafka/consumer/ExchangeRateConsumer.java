package com.proxy.exchangerate.kafka.consumer;

import com.proxy.exchangerate.model.ExchangeRateDocument;
import com.proxy.exchangerate.repository.ExchangeRateRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Consommateur Kafka — lit les taux de change et les indexe dans Elasticsearch.
 * Un consumer par partition (3 threads = 3 partitions).
 */
@Component
public class ExchangeRateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateConsumer.class);

    private final ExchangeRateRepository repository;

    public ExchangeRateConsumer(ExchangeRateRepository repository) {
        this.repository = repository;
    }

    /**
     * Écoute le topic "exchange-rates" et sauvegarde dans Elasticsearch.
     *
     * @param record enregistrement Kafka contenant le document taux de change
     */
    @KafkaListener(
            topics = "exchange-rates",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, ExchangeRateDocument> record) {
        try {
            ExchangeRateDocument doc = record.value();

            if (doc == null) {
                log.warn("[CONSUMER] Message null ignoré — partition={}, offset={}",
                        record.partition(), record.offset());
                return;
            }

            log.info("[CONSUMER] 📥 Reçu — base={}, partition={}, offset={}",
                    doc.getBase(), record.partition(), record.offset());

            // Enrichissement indexedAt si absent
            if (doc.getIndexedAt() == null) {
                doc.setIndexedAt(LocalDateTime.now());
            }

            // Sauvegarde Elasticsearch
            repository.save(doc);

            log.info("[CONSUMER] ✅ Indexé ES — base={}, id={}, devises={}",
                    doc.getBase(), doc.getId(),
                    doc.getRates() != null ? doc.getRates().size() : 0);

        } catch (Exception e) {
            log.error("[CONSUMER] ❌ Erreur traitement — partition={}, offset={}, erreur={}",
                    record.partition(), record.offset(), e.getMessage(), e);
            // Ne pas relancer l'exception pour éviter le requeue infini
            // En production : envoyer dans une Dead Letter Queue (DLQ)
        }
    }
}

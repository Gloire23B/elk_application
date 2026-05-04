package com.proxy.exchangerate.kafka.consumer;

import com.proxy.exchangerate.model.ExchangeRateDocument;
import com.proxy.exchangerate.repository.ExchangeRateRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du ExchangeRateConsumer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests ExchangeRateConsumer")
class ExchangeRateConsumerTest {

    @Mock
    private ExchangeRateRepository repository;

    @InjectMocks
    private ExchangeRateConsumer consumer;

    private ExchangeRateDocument validDoc;

    @BeforeEach
    void setUp() {
        validDoc = ExchangeRateDocument.builder()
                .id("USD_2026-04-20_test")
                .base("USD")
                .timestamp("2026-04-20")
                .rates(Map.of("EUR", 0.92, "GBP", 0.79))
                .build();
    }

    private ConsumerRecord<String, ExchangeRateDocument> buildRecord(ExchangeRateDocument doc) {
        return new ConsumerRecord<>("exchange-rates", 0, 0L, doc != null ? doc.getBase() : null, doc);
    }

    @Test
    @DisplayName("consume — doit sauvegarder le document dans Elasticsearch")
    void consume_shouldSaveToElasticsearch_whenMessageIsValid() {
        ConsumerRecord<String, ExchangeRateDocument> record = buildRecord(validDoc);

        consumer.consume(record);

        verify(repository, times(1)).save(validDoc);
    }

    @Test
    @DisplayName("consume — doit ignorer un message null sans lever d'exception")
    void consume_shouldIgnoreNullMessage() {
        ConsumerRecord<String, ExchangeRateDocument> record = buildRecord(null);

        assertDoesNotThrow(() -> consumer.consume(record));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("consume — doit injecter indexedAt si absent")
    void consume_shouldSetIndexedAt_whenMissing() {
        validDoc.setIndexedAt(null);
        ConsumerRecord<String, ExchangeRateDocument> record = buildRecord(validDoc);

        consumer.consume(record);

        // indexedAt doit avoir été enrichi avant la sauvegarde
        verify(repository, times(1)).save(argThat(doc -> doc.getIndexedAt() != null));
    }

    @Test
    @DisplayName("consume — doit gérer une exception Elasticsearch sans propager")
    void consume_shouldHandleRepositoryException_gracefully() {
        doThrow(new RuntimeException("Elasticsearch indisponible"))
                .when(repository).save(any());

        ConsumerRecord<String, ExchangeRateDocument> record = buildRecord(validDoc);

        assertDoesNotThrow(() -> consumer.consume(record));
    }
}

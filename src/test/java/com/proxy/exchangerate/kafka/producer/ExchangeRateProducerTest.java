package com.proxy.exchangerate.kafka.producer;

import com.proxy.exchangerate.model.ExchangeRateDocument;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du ExchangeRateProducer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests ExchangeRateProducer")
class ExchangeRateProducerTest {

    @Mock
    private KafkaTemplate<String, ExchangeRateDocument> kafkaTemplate;

    @InjectMocks
    private ExchangeRateProducer producer;

    private ExchangeRateDocument doc;

    @BeforeEach
    void setUp() {
        doc = ExchangeRateDocument.builder()
                .id("USD_2026-04-20_test")
                .base("USD")
                .timestamp("2026-04-20")
                .rates(Map.of("EUR", 0.92, "GBP", 0.79))
                .build();
    }

    @Test
    @DisplayName("publish — doit appeler kafkaTemplate.send avec la clé = base currency")
    void publish_shouldSendWithCorrectKey() {
        // Arrange
        CompletableFuture<SendResult<String, ExchangeRateDocument>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq("exchange-rates"), eq("USD"), eq(doc))).thenReturn(future);

        // Act
        producer.publish(doc);

        // Assert — BUG #5 vérifié : clé = "USD", pas null
        verify(kafkaTemplate, times(1)).send("exchange-rates", "USD", doc);
    }

    @Test
    @DisplayName("publish — doit ignorer silencieusement un document null")
    void publish_shouldIgnoreNullDocument() {
        assertDoesNotThrow(() -> producer.publish(null));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("publish — doit ignorer un document sans base currency")
    void publish_shouldIgnoreDocumentWithNullBase() {
        ExchangeRateDocument noBaseDoc = ExchangeRateDocument.builder()
                .id("test")
                .rates(Map.of("EUR", 0.92))
                .build(); // base = null

        assertDoesNotThrow(() -> producer.publish(noBaseDoc));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("publish — doit gérer une erreur kafka sans propager l'exception")
    void publish_shouldHandleKafkaFailureGracefully() {
        CompletableFuture<SendResult<String, ExchangeRateDocument>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        assertDoesNotThrow(() -> producer.publish(doc));
    }

    @Test
    @DisplayName("publish — doit logguer le succès avec partition et offset")
    void publish_shouldLogSuccessOnCompletion() {
        ProducerRecord<String, ExchangeRateDocument> record =
                new ProducerRecord<>("exchange-rates", "USD", doc);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("exchange-rates", 1), 42L, 0, 0L, 0, 0);
        SendResult<String, ExchangeRateDocument> sendResult = new SendResult<>(record, metadata);
        CompletableFuture<SendResult<String, ExchangeRateDocument>> future =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        assertDoesNotThrow(() -> producer.publish(doc));
        verify(kafkaTemplate, times(1)).send("exchange-rates", "USD", doc);
    }
}

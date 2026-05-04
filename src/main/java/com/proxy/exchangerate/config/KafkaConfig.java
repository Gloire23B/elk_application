package com.proxy.exchangerate.config;

import com.proxy.exchangerate.model.ExchangeRateDocument;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Kafka — Producer, Consumer, Topics.
 *
 * BUG #1 ANTICIPÉ ET CORRIGÉ :
 *   JsonDeserializer.TRUSTED_PACKAGES non configuré → ClassNotFoundException.
 *   Solution : trusted packages configuré sur com.proxy.exchangerate.model.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    public static final String EXCHANGE_RATES_TOPIC = "exchange-rates";

    // ===================== TOPICS =====================

    @Bean
    public NewTopic exchangeRatesTopic() {
        return TopicBuilder.name(EXCHANGE_RATES_TOPIC)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "604800000") // 7 jours
                .build();
    }

    // ===================== PRODUCER =====================

    @Bean
    public ProducerFactory<String, ExchangeRateDocument> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, ExchangeRateDocument> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ===================== CONSUMER =====================

    @Bean
    public ConsumerFactory<String, ExchangeRateDocument> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // BUG #1 CORRIGÉ : trusted packages configuré
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.proxy.exchangerate.model");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(ExchangeRateDocument.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExchangeRateDocument>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ExchangeRateDocument> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3); // 3 threads = 3 partitions
        return factory;
    }
}

package com.exchangerate.proxy.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.Map;

/**
 * Modèle Elasticsearch pour les taux de change.
 *
 * <p>Index : {@code exchange-rates}
 *
 * <p>Mapping optimisé :
 * <ul>
 *   <li>{@code base}      : keyword — filtrage exact ultra-rapide</li>
 *   <li>{@code timestamp} : date    — tri chronologique, range queries</li>
 *   <li>{@code rates}     : Object  — requêtes sur sous-champs (rates.EUR)</li>
 *   <li>{@code source}    : keyword — audit et traçabilité</li>
 * </ul>
 *
 * <p>Stratégie d'ID : {@code {base}_{timestamp_epoch}} pour idempotence.
 * Ex: "USD_1713600000" — un upsert sur cet ID ne crée jamais de doublon.
 *
 * CORRECTION : Suppression de @Setting(settingPath=...) dont le chemin
 * classpath peut être instable selon les builds. Les settings sont maintenant
 * dans @Document directement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(
    indexName = "exchange-rates",
    createIndex = true
)
@Setting(shards = 3, replicas = 1, refreshInterval = "5s")
public class ExchangeRate {

    /**
     * ID composite : {base}_{timestamp_epoch}
     * Ex: "USD_1713600000" — garantit l'unicité et l'idempotence.
     */
    @Id
    private String id;

    /** Devise de base (keyword pour filtrage exact). */
    @Field(type = FieldType.Keyword)
    private String base;

    /** Timestamp de collecte (format ISO-8601 UTC). */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    @JsonFormat(
        shape     = JsonFormat.Shape.STRING,
        pattern   = "yyyy-MM-dd'T'HH:mm:ss'Z'",
        timezone  = "UTC"
    )
    private Instant timestamp;

    /**
     * Taux de change indexés par devise.
     * Type Object pour requêtes sur sous-champs : rates.EUR, rates.GBP...
     */
    @Field(type = FieldType.Object)
    private Map<String, Double> rates;

    /** Source de la donnée pour traçabilité. */
    @Field(type = FieldType.Keyword)
    private String source;

    /** Date d'indexation dans Elasticsearch (audit). */
    @Field(type = FieldType.Date, format = DateFormat.date_time, name = "indexed_at")
    private Instant indexedAt;

    /** Nombre de devises dans ce snapshot. */
    @Field(type = FieldType.Integer, name = "currency_count")
    private Integer currencyCount;
}

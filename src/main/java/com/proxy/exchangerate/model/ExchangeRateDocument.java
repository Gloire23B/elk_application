package com.proxy.exchangerate.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Document Elasticsearch — Taux de change.
 *
 * Format JSON Kafka standard :
 * {
 *   "base": "USD",
 *   "timestamp": "2026-04-20",
 *   "rates": {}
 * }
 *
 * BUG #3 ANTICIPÉ ET CORRIGÉ :
 *   L'index Elasticsearch était défini en dur "exchange-rates" sans suffixe de date.
 *   Solution : indexNameFunction utilisé (voir ExchangeRateRepository) pour indexation mensuelle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "exchange-rates")
public class ExchangeRateDocument {

    /** Identifiant ES unique : base_timestamp */
    @Id
    private String id;

    /** Devise de base (ex: USD, EUR) */
    @Field(type = FieldType.Keyword)
    private String base;

    /** Date de récupération (format ISO) */
    @Field(type = FieldType.Keyword, name = "timestamp")
    private String timestamp;

    /** Carte des taux : devise → taux */
    @Field(type = FieldType.Object)
    private Map<String, Double> rates;

    /** Horodatage d'indexation */
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime indexedAt;

    /** Source des données */
    @Field(type = FieldType.Keyword)
    private String source;

    /**
     * Retourne le taux pour une devise donnée.
     * @param currency code devise (ex: "EUR")
     * @return taux ou null si non disponible
     */
    public Double getRateFor(String currency) {
        if (rates == null || currency == null) return null;
        return rates.get(currency.toUpperCase());
    }
}

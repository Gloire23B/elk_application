package com.proxy.exchangerate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO — Réponse de l'API externe exchangerate-api.com.
 *
 * Exemple de réponse :
 * {
 *   "result": "success",
 *   "base_code": "USD",
 *   "time_last_update_utc": "Fri, 20 Apr 2026 00:00:01 +0000",
 *   "rates": { "EUR": 0.92, "GBP": 0.79, ... }
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeRateApiResponse {

    @JsonProperty("result")
    private String result;

    @JsonProperty("base_code")
    private String baseCode;

    /** Compatibilité avec l'endpoint /v4/latest/{base} */
    @JsonProperty("base")
    private String base;

    @JsonProperty("date")
    private String date;

    @JsonProperty("time_last_update_utc")
    private String timeLastUpdateUtc;

    @JsonProperty("time_next_update_utc")
    private String timeNextUpdateUtc;

    @JsonProperty("rates")
    private Map<String, Double> rates;

    /**
     * Retourne le code de devise de base, quel que soit le format de l'API.
     */
    public String resolveBase() {
        if (baseCode != null && !baseCode.isBlank()) return baseCode;
        if (base != null && !base.isBlank()) return base;
        return "USD";
    }

    /**
     * Vérifie que la réponse est valide.
     */
    public boolean isValid() {
        return rates != null && !rates.isEmpty() && resolveBase() != null;
    }
}

package com.proxy.exchangerate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Enveloppe générique pour toutes les réponses REST de l'API.
 *
 * Format standard :
 * {
 *   "success": true,
 *   "data": { ... },
 *   "error": null,
 *   "timestamp": "2026-04-20T10:00:00Z"
 * }
 *
 * @param <T> type de la donnée encapsulée
 */
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String error;
    private String timestamp;

    private ApiResponse(boolean success, T data, String error) {
        this.success   = success;
        this.data      = data;
        this.error     = error;
        this.timestamp = Instant.now().toString();
    }

    /**
     * Réponse de succès avec données.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * Réponse d'erreur avec message.
     */
    public static <T> ApiResponse<T> error(String errorMessage) {
        return new ApiResponse<>(false, null, errorMessage);
    }
}

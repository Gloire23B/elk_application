package com.proxy.exchangerate.controller;

import com.proxy.exchangerate.dto.ApiResponse;
import com.proxy.exchangerate.model.ExchangeRateDocument;
import com.proxy.exchangerate.service.ExchangeRateService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST — API de taux de change.
 *
 * Endpoints :
 *   GET  /api/exchange-rates/latest/{base}              — derniers taux
 *   GET  /api/exchange-rates/rate?from=USD&to=EUR       — taux entre 2 devises
 *   GET  /api/exchange-rates/history/{base}?from=&to=   — historique
 *   POST /api/exchange-rates/refresh/{base}             — forcer un refresh
 */
@RestController
@RequestMapping("/api/exchange-rates")
@Validated
public class ExchangeRateController {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateController.class);

    /** Pattern de validation : 3 lettres majuscules (ISO 4217) */
    private static final String CURRENCY_PATTERN = "^[A-Z]{3}$";

    private final ExchangeRateService service;

    public ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    /**
     * Retourne les derniers taux de change pour une devise de base.
     *
     * @param base devise de base (ex: USD)
     * @return ApiResponse contenant ExchangeRateDocument
     */
    @GetMapping("/latest/{base}")
    public ResponseEntity<ApiResponse<ExchangeRateDocument>> getLatestRates(
            @PathVariable
            @NotBlank(message = "La devise de base est obligatoire")
            @Pattern(regexp = CURRENCY_PATTERN, message = "La devise doit être un code ISO 4217 (ex: USD, EUR)")
            String base) {

        log.info("[CONTROLLER] GET /latest/{}", base);

        return service.getLatestRates(base.toUpperCase())
                .map(doc -> ResponseEntity.ok(ApiResponse.success(doc)))
                .orElseGet(() -> {
                    // Pas en cache → fetch depuis l'API externe
                    ExchangeRateDocument doc = service.fetchAndPublish(base.toUpperCase());
                    return ResponseEntity.ok(ApiResponse.success(doc));
                });
    }

    /**
     * Retourne le taux de conversion entre deux devises.
     *
     * @param from devise source
     * @param to   devise cible
     * @return taux de conversion
     */
    @GetMapping("/rate")
    public ResponseEntity<ApiResponse<Double>> getRate(
            @RequestParam
            @NotBlank @Pattern(regexp = CURRENCY_PATTERN) String from,
            @RequestParam
            @NotBlank @Pattern(regexp = CURRENCY_PATTERN) String to) {

        log.info("[CONTROLLER] GET /rate?from={}&to={}", from, to);

        Double rate = service.getRate(from.toUpperCase(), to.toUpperCase());
        if (rate == null) {
            return ResponseEntity.ok(ApiResponse.error("Taux non disponible pour " + from + " → " + to));
        }
        return ResponseEntity.ok(ApiResponse.success(rate));
    }

    /**
     * Retourne l'historique des taux pour une devise sur une période.
     *
     * @param base     devise de base
     * @param fromDate date de début (yyyy-MM-dd)
     * @param toDate   date de fin   (yyyy-MM-dd)
     * @return liste des taux historiques
     */
    @GetMapping("/history/{base}")
    public ResponseEntity<ApiResponse<List<ExchangeRateDocument>>> getRatesHistory(
            @PathVariable @Pattern(regexp = CURRENCY_PATTERN) String base,
            @RequestParam @NotBlank @Size(min = 10, max = 10) String fromDate,
            @RequestParam @NotBlank @Size(min = 10, max = 10) String toDate) {

        log.info("[CONTROLLER] GET /history/{}?from={}&to={}", base, fromDate, toDate);

        List<ExchangeRateDocument> history =
                service.getRatesHistory(base.toUpperCase(), fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * Force un refresh des taux depuis l'API externe.
     *
     * @param base devise de base à rafraîchir
     * @return document fraîchement récupéré
     */
    @PostMapping("/refresh/{base}")
    public ResponseEntity<ApiResponse<ExchangeRateDocument>> forceRefresh(
            @PathVariable @Pattern(regexp = CURRENCY_PATTERN) String base) {

        log.info("[CONTROLLER] POST /refresh/{} — refresh forcé", base);
        ExchangeRateDocument doc = service.fetchAndPublish(base.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(doc));
    }
}

package com.exchangerate.proxy.controller;

import com.exchangerate.proxy.model.ExchangeRate;
import com.exchangerate.proxy.service.ExchangeRateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests du Controller REST via Spring MockMvc.
 *
 * <p>Couverture :
 * <ul>
 *   <li>GET /{base}          — 200 avec données / 404 si absent</li>
 *   <li>GET /{base}/history  — 200 liste</li>
 *   <li>GET /{base}/{target} — 200 taux / 404</li>
 *   <li>POST /refresh/{base} — 200 déclenchement</li>
 *   <li>GET /{base}          — 400 si devise invalide (validation)</li>
 * </ul>
 */
@WebMvcTest(ExchangeRateController.class)
@Import(com.exchangerate.proxy.config.SecurityConfig.class)
// CORRECTION : @TestPropertySource fournit les @Value requis par SecurityConfig.
// Sans cela : BeanCreationException car app.security.api-key-header non résolu.
@TestPropertySource(properties = {
    "app.security.api-key-header=X-API-Key",
    "app.security.api-key=dev-secret-key-change-in-production"
})
@DisplayName("ExchangeRateController — Tests d'intégration Web")
class ExchangeRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExchangeRateService service;

    // API key pour les tests
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE  = "dev-secret-key-change-in-production";

    // ==================== GET LATEST RATES ====================

    @Test
    @DisplayName("GET /v1/rates/USD — 200 avec données")
    void getLatestRates_ExistingData_Returns200() throws Exception {
        // GIVEN
        ExchangeRate rate = buildExchangeRate("USD");
        when(service.getLatestRates("USD")).thenReturn(Optional.of(rate));

        // WHEN / THEN
        mockMvc.perform(get("/v1/rates/USD")
                .header(API_KEY_HEADER, API_KEY_VALUE)
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.base").value("USD"))
                .andExpect(jsonPath("$.data.rates.EUR").value(0.93));
    }

    @Test
    @DisplayName("GET /v1/rates/XYZ — 404 si aucune donnée")
    void getLatestRates_NoData_Returns404() throws Exception {
        // GIVEN
        when(service.getLatestRates("XYZ")).thenReturn(Optional.empty());

        // WHEN / THEN
        mockMvc.perform(get("/v1/rates/XYZ")
                .header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /v1/rates/US — 400 si devise invalide (2 lettres)")
    void getLatestRates_InvalidCurrency_Returns400() throws Exception {
        mockMvc.perform(get("/v1/rates/US")
                .header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/rates/USD — 401 sans API Key")
    void getLatestRates_NoApiKey_Returns401() throws Exception {
        mockMvc.perform(get("/v1/rates/USD"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET HISTORY ====================

    @Test
    @DisplayName("GET /v1/rates/USD/history — 200 avec liste")
    void getHistory_Returns200WithList() throws Exception {
        // GIVEN
        when(service.getHistoricalRates("USD", 24))
                .thenReturn(List.of(buildExchangeRate("USD"), buildExchangeRate("USD")));

        // WHEN / THEN
        mockMvc.perform(get("/v1/rates/USD/history?hours=24")
                .header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /v1/rates/USD/history?hours=200 — 400 (>168h)")
    void getHistory_HoursExceedMax_Returns400() throws Exception {
        mockMvc.perform(get("/v1/rates/USD/history?hours=200")
                .header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET SPECIFIC RATE ====================

    @Test
    @DisplayName("GET /v1/rates/USD/EUR — 200 avec taux")
    void getSpecificRate_Returns200() throws Exception {
        // GIVEN
        when(service.getSpecificRate("USD", "EUR")).thenReturn(Optional.of(0.93));

        // WHEN / THEN
        mockMvc.perform(get("/v1/rates/USD/EUR")
                .header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.base").value("USD"))
                .andExpect(jsonPath("$.data.target").value("EUR"))
                .andExpect(jsonPath("$.data.rate").value(0.93));
    }

    @Test
    @DisplayName("GET /v1/rates/USD/XYZ — 404 si taux absent")
    void getSpecificRate_NotFound_Returns404() throws Exception {
        // GIVEN
        when(service.getSpecificRate("USD", "XYZ")).thenReturn(Optional.empty());

        // WHEN / THEN
        mockMvc.perform(get("/v1/rates/USD/XYZ")
                .header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isNotFound());
    }

    // ==================== REFRESH ====================

    @Test
    @DisplayName("POST /v1/rates/refresh/USD — 200 déclenche refresh")
    void refreshRates_Returns200() throws Exception {
        // GIVEN
        doNothing().when(service).fetchAndPublish("USD");

        // WHEN / THEN
        mockMvc.perform(post("/v1/rates/refresh/USD")
                .header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());

        verify(service, times(1)).fetchAndPublish("USD");
    }

    // ==================== STATS ====================

    @Test
    @DisplayName("GET /v1/rates/stats — 200 avec statistiques")
    void getStats_Returns200() throws Exception {
        // GIVEN
        when(service.getIndexStats()).thenReturn(Map.of("USD", 1440L, "EUR", 1440L));

        // WHEN / THEN
        mockMvc.perform(get("/v1/rates/stats")
                .header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.USD").value(1440));
    }

    // ==================== HELPER ====================

    private ExchangeRate buildExchangeRate(String base) {
        return ExchangeRate.builder()
                .id(base + "_1713600000")
                .base(base)
                .timestamp(Instant.now())
                .rates(Map.of("EUR", 0.93, "GBP", 0.79))
                .source("exchangerate-api.com")
                .currencyCount(2)
                .build();
    }
}

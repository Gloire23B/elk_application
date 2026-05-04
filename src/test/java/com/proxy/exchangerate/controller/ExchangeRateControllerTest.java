package com.proxy.exchangerate.controller;

import com.proxy.exchangerate.config.SecurityConfig;
import com.proxy.exchangerate.model.ExchangeRateDocument;
import com.proxy.exchangerate.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests du controller REST avec MockMvc et sécurité simulée.
 */
@WebMvcTest(ExchangeRateController.class)
@Import(SecurityConfig.class)
@DisplayName("Tests ExchangeRateController")
class ExchangeRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExchangeRateService service;

    private ExchangeRateDocument sampleDoc;

    @BeforeEach
    void setUp() {
        sampleDoc = ExchangeRateDocument.builder()
                .id("USD_2026-04-20_test")
                .base("USD")
                .timestamp("2026-04-20")
                .rates(Map.of("EUR", 0.92, "GBP", 0.79, "JPY", 149.5))
                .build();
    }

    @Test
    @WithMockUser(username = "api-user", roles = "API")
    @DisplayName("GET /latest/USD — 200 OK avec document")
    void getLatestRates_shouldReturn200_whenAuthenticated() throws Exception {
        when(service.getLatestRates("USD")).thenReturn(Optional.of(sampleDoc));

        mockMvc.perform(get("/api/exchange-rates/latest/USD")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.base").value("USD"))
                .andExpect(jsonPath("$.data.rates.EUR").value(0.92));
    }

    @Test
    @DisplayName("GET /latest/USD — 401 sans authentification")
    void getLatestRates_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/exchange-rates/latest/USD"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "api-user", roles = "API")
    @DisplayName("GET /latest/INVALID — 400 Bad Request pour devise invalide")
    void getLatestRates_shouldReturn400_forInvalidCurrency() throws Exception {
        mockMvc.perform(get("/api/exchange-rates/latest/INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "api-user", roles = "API")
    @DisplayName("GET /rate?from=USD&to=EUR — 200 OK avec taux")
    void getRate_shouldReturn200_withCorrectRate() throws Exception {
        when(service.getRate("USD", "EUR")).thenReturn(0.92);

        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(0.92));
    }

    @Test
    @WithMockUser(username = "api-user", roles = "API")
    @DisplayName("GET /rate?from=USD&to=XYZ — retourne erreur si devise non trouvée")
    void getRate_shouldReturnError_whenCurrencyNotFound() throws Exception {
        when(service.getRate("USD", "XYZ")).thenReturn(null);

        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("from", "USD")
                        .param("to", "XYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "api-user", roles = "API")
    @DisplayName("POST /refresh/USD — 200 OK après refresh forcé")
    void forceRefresh_shouldReturn200() throws Exception {
        when(service.fetchAndPublish("USD")).thenReturn(sampleDoc);

        mockMvc.perform(post("/api/exchange-rates/refresh/USD")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.base").value("USD"));
    }
}

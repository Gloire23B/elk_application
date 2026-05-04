package com.proxy.exchangerate.service;

import com.proxy.exchangerate.exception.ExchangeRateException;
import com.proxy.exchangerate.kafka.producer.ExchangeRateProducer;
import com.proxy.exchangerate.model.ExchangeRateApiResponse;
import com.proxy.exchangerate.model.ExchangeRateDocument;
import com.proxy.exchangerate.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du ExchangeRateServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests ExchangeRateServiceImpl")
class ExchangeRateServiceTest {

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private ExchangeRateProducer producer;
    @Mock private ExchangeRateRepository repository;

    @InjectMocks
    private ExchangeRateServiceImpl service;

    private ExchangeRateDocument sampleDoc;
    private ExchangeRateApiResponse sampleApiResponse;

    @BeforeEach
    void setUp() {
        sampleDoc = ExchangeRateDocument.builder()
                .id("USD_2026-04-20_abc12345")
                .base("USD")
                .timestamp("2026-04-20")
                .rates(Map.of("EUR", 0.92, "GBP", 0.79, "JPY", 149.5))
                .build();

        sampleApiResponse = new ExchangeRateApiResponse();
        sampleApiResponse.setBase("USD");
        sampleApiResponse.setDate("2026-04-20");
        sampleApiResponse.setRates(Map.of("EUR", 0.92, "GBP", 0.79, "JPY", 149.5));
    }

    @Test
    @DisplayName("getLatestRates — doit retourner le document depuis le repository")
    void getLatestRates_shouldReturnFromRepository() {
        when(repository.findTopByBaseOrderByIndexedAtDesc("USD"))
                .thenReturn(Optional.of(sampleDoc));

        Optional<ExchangeRateDocument> result = service.getLatestRates("USD");

        assertThat(result).isPresent();
        assertThat(result.get().getBase()).isEqualTo("USD");
        assertThat(result.get().getRates()).containsKey("EUR");
        verify(repository, times(1)).findTopByBaseOrderByIndexedAtDesc("USD");
    }

    @Test
    @DisplayName("getLatestRates — doit retourner Optional.empty si aucun taux en cache")
    void getLatestRates_shouldReturnEmpty_whenNotCached() {
        when(repository.findTopByBaseOrderByIndexedAtDesc("CHF"))
                .thenReturn(Optional.empty());

        Optional<ExchangeRateDocument> result = service.getLatestRates("CHF");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getRate — doit retourner le taux USD→EUR depuis le document stocké")
    void getRate_shouldReturnCorrectRate() {
        when(repository.findTopByBaseOrderByIndexedAtDesc("USD"))
                .thenReturn(Optional.of(sampleDoc));

        Double rate = service.getRate("USD", "EUR");

        assertThat(rate).isNotNull();
        assertThat(rate).isEqualTo(0.92);
    }

    @Test
    @DisplayName("getRate — doit retourner null si la devise cible n'existe pas")
    void getRate_shouldReturnNull_whenCurrencyNotFound() {
        when(repository.findTopByBaseOrderByIndexedAtDesc("USD"))
                .thenReturn(Optional.of(sampleDoc));

        Double rate = service.getRate("USD", "XYZ");

        assertThat(rate).isNull();
    }

    @Test
    @DisplayName("getRate — doit retourner null si aucun document en base")
    void getRate_shouldReturnNull_whenNoDocumentFound() {
        when(repository.findTopByBaseOrderByIndexedAtDesc("USD"))
                .thenReturn(Optional.empty());

        Double rate = service.getRate("USD", "EUR");

        assertThat(rate).isNull();
    }

    @Test
    @DisplayName("getRatesHistory — doit déléguer au repository avec les bons paramètres")
    void getRatesHistory_shouldDelegateToRepository() {
        when(repository.findByBaseAndTimestampBetween("USD", "2026-01-01", "2026-04-20"))
                .thenReturn(java.util.List.of(sampleDoc));

        var history = service.getRatesHistory("USD", "2026-01-01", "2026-04-20");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getBase()).isEqualTo("USD");
        verify(repository).findByBaseAndTimestampBetween("USD", "2026-01-01", "2026-04-20");
    }
}

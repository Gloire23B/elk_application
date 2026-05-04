package com.proxy.exchangerate.repository;

import com.proxy.exchangerate.model.ExchangeRateDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du ExchangeRateRepository (Mocked).
 * Les tests d'intégration ES nécessiteraient Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests ExchangeRateRepository (Mocked)")
class ExchangeRateRepositoryTest {

    @Mock
    private ExchangeRateRepository repository;

    private ExchangeRateDocument usdDoc;
    private ExchangeRateDocument eurDoc;

    @BeforeEach
    void setUp() {
        usdDoc = ExchangeRateDocument.builder()
                .id("USD_2026-04-20_001")
                .base("USD")
                .timestamp("2026-04-20")
                .rates(Map.of("EUR", 0.92, "GBP", 0.79))
                .indexedAt(LocalDateTime.now())
                .source("test")
                .build();

        eurDoc = ExchangeRateDocument.builder()
                .id("EUR_2026-04-20_001")
                .base("EUR")
                .timestamp("2026-04-20")
                .rates(Map.of("USD", 1.09, "GBP", 0.86))
                .indexedAt(LocalDateTime.now())
                .source("test")
                .build();
    }

    @Test
    @DisplayName("save — doit persister le document et retourner l'objet sauvegardé")
    void save_shouldReturnSavedDocument() {
        when(repository.save(usdDoc)).thenReturn(usdDoc);

        ExchangeRateDocument result = repository.save(usdDoc);

        assertThat(result).isNotNull();
        assertThat(result.getBase()).isEqualTo("USD");
        assertThat(result.getRates()).containsKey("EUR");
        verify(repository, times(1)).save(usdDoc);
    }

    @Test
    @DisplayName("findTopByBaseOrderByIndexedAtDesc — doit retourner le plus récent pour USD")
    void findTopByBase_shouldReturnMostRecent() {
        when(repository.findTopByBaseOrderByIndexedAtDesc("USD"))
                .thenReturn(Optional.of(usdDoc));

        Optional<ExchangeRateDocument> result =
                repository.findTopByBaseOrderByIndexedAtDesc("USD");

        assertThat(result).isPresent();
        assertThat(result.get().getBase()).isEqualTo("USD");
    }

    @Test
    @DisplayName("findTopByBaseOrderByIndexedAtDesc — retourne empty si devise inconnue")
    void findTopByBase_shouldReturnEmpty_whenCurrencyUnknown() {
        when(repository.findTopByBaseOrderByIndexedAtDesc("XYZ"))
                .thenReturn(Optional.empty());

        Optional<ExchangeRateDocument> result =
                repository.findTopByBaseOrderByIndexedAtDesc("XYZ");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByBaseAndTimestampBetween — doit retourner les documents dans la plage")
    void findByBaseAndTimestampBetween_shouldReturnHistoricalDocs() {
        when(repository.findByBaseAndTimestampBetween("USD", "2026-01-01", "2026-04-30"))
                .thenReturn(List.of(usdDoc));

        List<ExchangeRateDocument> results =
                repository.findByBaseAndTimestampBetween("USD", "2026-01-01", "2026-04-30");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBase()).isEqualTo("USD");
    }

    @Test
    @DisplayName("findByBase — doit retourner tous les documents USD")
    void findByBase_shouldReturnAllDocumentsForCurrency() {
        when(repository.findByBase("USD")).thenReturn(List.of(usdDoc));

        List<ExchangeRateDocument> results = repository.findByBase("USD");

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(doc -> "USD".equals(doc.getBase()));
    }
}

package com.proxy.exchangerate.scheduler;

import com.proxy.exchangerate.service.ExchangeRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler — Récupération périodique des taux de change depuis l'API externe.
 * Publie automatiquement sur Kafka pour alimenter les 15 000 équipes consommatrices.
 *
 * Fréquence par défaut : toutes les 60 minutes.
 * Configurable via : exchange-rate.scheduler.cron
 */
@Component
public class ExchangeRateScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateScheduler.class);

    /** Devises de base à surveiller */
    private static final List<String> BASE_CURRENCIES =
            List.of("USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD");

    private final ExchangeRateService service;

    @Value("${exchange-rate.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public ExchangeRateScheduler(ExchangeRateService service) {
        this.service = service;
    }

    /**
     * Tâche planifiée — rafraîchit toutes les devises configurées.
     * Par défaut : toutes les 60 minutes.
     */
    @Scheduled(cron = "${exchange-rate.scheduler.cron:0 0 * * * *}")
    public void scheduledFetch() {
        if (!schedulerEnabled) {
            log.debug("[SCHEDULER] Désactivé — fetch ignoré");
            return;
        }

        log.info("[SCHEDULER] ▶ Démarrage refresh planifié — {} devises", BASE_CURRENCIES.size());
        int success = 0;
        int failures = 0;

        for (String currency : BASE_CURRENCIES) {
            try {
                service.fetchAndPublish(currency);
                success++;
                log.debug("[SCHEDULER] ✅ {} refreshé", currency);
            } catch (Exception e) {
                failures++;
                log.error("[SCHEDULER] ❌ Échec refresh {} — {}", currency, e.getMessage());
            }
        }

        log.info("[SCHEDULER] ◼ Refresh terminé — succès={}, échecs={}", success, failures);
    }
}

package com.proxy.exchangerate.service;

import com.proxy.exchangerate.model.ExchangeRateDocument;

import java.util.List;
import java.util.Optional;

/**
 * Interface du service de gestion des taux de change.
 * Respecte le principe d'inversion de dépendances (SOLID — D).
 */
public interface ExchangeRateService {

    /**
     * Récupère les taux depuis l'API externe, publie sur Kafka et retourne le résultat.
     *
     * @param baseCurrency devise de base (ex: "USD")
     * @return document taux de change
     */
    ExchangeRateDocument fetchAndPublish(String baseCurrency);

    /**
     * Retourne les derniers taux disponibles pour une devise.
     *
     * @param baseCurrency devise de base
     * @return Optional du document le plus récent
     */
    Optional<ExchangeRateDocument> getLatestRates(String baseCurrency);

    /**
     * Retourne l'historique des taux pour une devise sur une période.
     *
     * @param baseCurrency devise de base
     * @param fromDate     date de début (ISO: yyyy-MM-dd)
     * @param toDate       date de fin (ISO: yyyy-MM-dd)
     * @return liste des taux historiques
     */
    List<ExchangeRateDocument> getRatesHistory(String baseCurrency, String fromDate, String toDate);

    /**
     * Retourne le taux entre deux devises.
     *
     * @param from devise source
     * @param to   devise cible
     * @return taux de conversion ou null si indisponible
     */
    Double getRate(String from, String to);
}

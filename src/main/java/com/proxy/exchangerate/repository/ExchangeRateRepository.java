package com.proxy.exchangerate.repository;

import com.proxy.exchangerate.model.ExchangeRateDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Elasticsearch pour les taux de change.
 * Spring Data Elasticsearch génère automatiquement les implémentations.
 */
@Repository
public interface ExchangeRateRepository
        extends ElasticsearchRepository<ExchangeRateDocument, String> {

    /**
     * Dernier taux pour une devise de base, trié par date d'indexation décroissante.
     *
     * @param base devise de base (ex: "USD")
     * @return Optional du document le plus récent
     */
    Optional<ExchangeRateDocument> findTopByBaseOrderByIndexedAtDesc(String base);

    /**
     * Historique des taux pour une devise sur une plage de dates.
     *
     * @param base      devise de base
     * @param fromDate  date début (format yyyy-MM-dd)
     * @param toDate    date fin   (format yyyy-MM-dd)
     * @return liste des documents triés par timestamp
     */
    List<ExchangeRateDocument> findByBaseAndTimestampBetween(
            String base, String fromDate, String toDate);

    /**
     * Tous les taux pour une devise de base.
     *
     * @param base devise de base
     * @return liste de tous les documents
     */
    List<ExchangeRateDocument> findByBase(String base);

    /**
     * Supprime tous les taux plus anciens qu'une date donnée.
     *
     * @param date date limite (format yyyy-MM-dd)
     */
    void deleteByTimestampBefore(String date);
}

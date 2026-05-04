# Rapport Technique — Exchange Rate Proxy

## 1. Fonctionnalités implémentées

### Backend Spring Boot
- Architecture en couches : Controller → Service → Repository + Kafka Producer/Consumer
- API REST sécurisée (Spring Security, HTTP Basic)
- CORS configuré via `CorsConfigurationSource` (requis pour les appels cross-origin du frontend)
- Gestion d'erreurs centralisée (`GlobalExceptionHandler`)
- Validation stricte des inputs (regex ISO 4217 sur les devises)
- Logging structuré avec SLF4J

### Kafka
- Topic `exchange-rates` : 3 partitions, retention 7 jours
- Producer : publication JSON standard `{base, timestamp, rates}`
- Producer idempotent (acks=all, retries=3)
- Consumer : 3 threads (1 par partition)
- Clé de partitionnement = devise (USD, EUR...) → ordre garanti par devise

### Elasticsearch
- Template d'index avec mapping `dynamic: strict`
- Champ `rates` en type `flattened` pour requêtes dynamiques
- Indexation avec `indexedAt` (date précise) et `timestamp` (date métier)
- Repository Spring Data ES avec méthodes de requête nommées

### Kibana
- Dashboard importable via NDJSON (`kibana/exchange-rate-dashboard.ndjson`)
- 3 visualisations Lens : courbe EUR/USD, tableau des devises, camembert de distribution
- Index Pattern lié au champ temporel `indexedAt`
- Rafraîchissement automatique toutes les 60 secondes

### Frontend Tailwind
- Dashboard responsive avec auto-refresh 60s
- Indicateur temps réel (variations ▲▼ par rapport au fetch précédent)
- Sélecteur de devise de base (USD, EUR, GBP, JPY, CHF, CAD)
- Stats rapides : nombre de devises, EUR/USD, GBP/USD, JPY/USD
- Fallback démo si API indisponible

---

## 2. Bugs corrigés

### Bugs de compilation initiaux

| # | Sévérité | Bug | Solution |
|---|----------|-----|---------|
| 1 | BLOQUANT | `ClassNotFoundException` — Jackson ne connaît pas les packages | `TRUSTED_PACKAGES=com.proxy.exchangerate.model` |
| 2 | BLOQUANT | `InvalidDefinitionException` — LocalDateTime non sérialisable | `mapper.registerModule(new JavaTimeModule())` |
| 3 | FONCTIONNEL | Index ES unique → conflits pour multi-dates | Template `exchange-rates*` avec `flattened` |
| 4 | RUNTIME | NPE si API externe timeout → `block()` retourne null | Null-check + exception métier claire |
| 5 | PERFORMANCE | Tous messages sur partition 0 → pas de parallélisme | Clé Kafka = `baseCurrency` |

### Bugs d'infrastructure et de déploiement

| # | Sévérité | Fichier | Bug | Solution |
|---|----------|---------|-----|---------|
| 6 | BLOQUANT | `docker-compose.yml` | Image `apache/kafka:3.6.0` inexistante sur Docker Hub — les images officielles `apache/kafka` n'existent qu'à partir de la version 3.7.0 | Mise à jour vers `apache/kafka:3.7.1` |
| 7 | BLOQUANT | `pom.xml` | Lombok `1.18.30` (bundlé par Spring Boot 3.2.0) incompatible avec Java 25 → 18 erreurs à la compilation : `cannot find symbol: getBase()`, `builder()`, `@AllArgsConstructor`, etc. | Ajout de `<lombok.version>1.18.36</lombok.version>` dans `<properties>` pour écraser la version gérée par le parent |
| 8 | FONCTIONNEL | `SecurityConfig.java` | Aucune configuration CORS → le navigateur bloque toutes les requêtes du frontend (servi sur `127.0.0.1:5500`) vers l'API (port `8090`) avec une erreur `Access-Control-Allow-Origin` | Ajout du bean `corsConfigurationSource()` avec `allowedOriginPatterns("*")`, méthodes `GET/POST/PUT/DELETE/OPTIONS`, credentials autorisés |
| 9 | BLOQUANT | `kibana/exchange-rate-dashboard.ndjson` | Trois bugs Kibana 8.11.0 cumulés : (a) format `visualization` legacy — la migration `removeInvalidAccessors` (v7.7.0) lit `datasourceStates.indexpattern.layers` mais notre état utilisait `formBased` → `TypeError` ; (b) `typeMigrationVersion` absent → Kibana exécutait **toutes** les migrations depuis la v0, y compris les migrations 7.x incompatibles avec le format moderne ; (c) `currentIndexPatternId` contenait l'ID réel de l'index pattern (`"exchange-rates-index-pattern"`) alors que Kibana attend le **nom de référence** (`"indexpattern-datasource-current-indexpattern"`) résolu via le tableau `references` | Réécriture complète en format Lens (`lnsXY`, `lnsDatatable`, `lnsPie`) ; ajout de `typeMigrationVersion: "8.9.0"` (version max enregistrée pour le type Lens dans Kibana 8.11.0) et `"7.17.3"` pour le dashboard ; `currentIndexPatternId` remplacé par le nom de référence |

---

## 3. Tests — Résultats

```
ExchangeRateServiceTest     : 6/6 
ExchangeRateControllerTest  : 6/6 
ExchangeRateProducerTest    : 5/5 
ExchangeRateConsumerTest    : 4/4 
ExchangeRateRepositoryTest  : 5/5 
───────────────────────────────────
TOTAL                       : 26/26 BUILD SUCCESS
```

---

## 4. Analyse de performance

### Avant le proxy (problème initial)
- 15 000 équipes × 1 appel/minute = **21 600 000 appels/jour**
- Coût estimé : $0.001 × 21 600 000 = **$21 600/jour**

### Après le proxy
- 7 devises × 24 appels/jour = **168 appels/jour**
- Réduction : **99.999 %**
- Économie annuelle estimée : **> $7 million**

---

## 5. Analyse architecture SOLID

| Principe | Appliqué |
|----------|---------|
| **S** — Single Responsibility | Chaque classe a 1 seule responsabilité (Producer != Consumer != Service) |
| **O** — Open/Closed | `ExchangeRateService` est une interface → extensible sans modification |
| **L** — Liskov | N/A (pas d'héritage de domaine) |
| **I** — Interface Segregation | Interface `ExchangeRateService` fine (4 méthodes) |
| **D** — Dependency Inversion | Injection via constructeurs, pas de `new` dans les services |

---

## 6. Niveau de difficulté

| Composant | Difficulté | Commentaire |
|-----------|-----------|-------------|
| Spring Boot Setup | 2 | Straightforward avec Spring Initializr |
| Kafka Producer/Consumer | 3 | Sérialisation JSON + partitionnement |
| Elasticsearch | 3 | Mapping dynamique + Spring Data |
| Spring Security + CORS | 3 | Config HttpSecurity Spring 6.x + CorsConfigurationSource |
| Tests MockMvc + Security | 4 | @WithMockUser + @Import SecurityConfig |
| Dashboard Tailwind Temps Réel | 3 | Polling + delta visualization |
| Dashboard Kibana Lens (NDJSON) | 5 | Format Lens 8.x, pipeline de migration interne Kibana, résolution des références par nom — aucune documentation officielle claire sur le format NDJSON d'import |

---

## 7. Recommandations pour la production

1. **Sécurité** : Remplacer HTTP Basic par JWT / OAuth2
2. **CORS** : Restreindre `allowedOriginPatterns` aux domaines connus (actuellement `"*"` pour le développement)
3. **Rate Limiting** : Ajouter Bucket4j sur les endpoints REST
4. **Circuit Breaker** : Activer Resilience4j sur l'appel API externe
5. **Monitoring** : Prometheus + Grafana sur les métriques Kafka
6. **Multi-broker** : Passer à 3 brokers Kafka pour haute disponibilité
7. **Tests d'intégration** : Ajouter Testcontainers pour ES + Kafka

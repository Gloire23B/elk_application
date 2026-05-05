# Exchange Rate Proxy — Documentation Complète

> Application industrielle de proxy centralisé de taux de change.
> **15 000 équipes** → 1 seule API externe → économie de 99% des appels facturés.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     ARCHITECTURE FLUX                           │
│                                                                 │
│  API Externe          Kafka           Elasticsearch   Frontend  │
│  exchangerate-api ──► Producer ──────► Consumer ──► ES Index ◄─┤
│  (1 appel/heure)      │ exchange-rates │             │          │
│                       │ 3 partitions   │             │          │
│                       └───────────────┘             │          │
│                                                     ▼          │
│  15 000 équipes ◄──── REST API Spring Boot ◄── GET /latest/{x} │
│                       (cache ES, 0 appel API externe)           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Structure du projet (32 fichiers)

```
exchange-rate-proxy/
├── pom.xml                                                    # (1)  Config Maven
├── Dockerfile                                                 # (2)  Image Docker
├── docker-compose.yml                                         # (3)  Infrastructure
├── src/main/java/com/proxy/exchangerate/
│   ├── ExchangeRateProxyApplication.java                      # (4)  Main
│   ├── config/
│   │   ├── KafkaConfig.java                                   # (5)  Config Kafka
│   │   ├── ElasticsearchConfig.java                           # (6)  Config ES
│   │   ├── SecurityConfig.java                                # (7)  Sécurité + CORS
│   │   └── WebClientConfig.java                               # (8)  WebClient
│   ├── model/
│   │   ├── ExchangeRateDocument.java                          # (9)  Modèle ES
│   │   └── ExchangeRateApiResponse.java                       # (10) DTO API
│   ├── service/
│   │   ├── ExchangeRateService.java                           # (11) Interface
│   │   └── ExchangeRateServiceImpl.java                       # (12) Implémentation
│   ├── kafka/
│   │   ├── producer/ExchangeRateProducer.java                 # (13) Producer Kafka
│   │   └── consumer/ExchangeRateConsumer.java                 # (14) Consumer Kafka
│   ├── repository/ExchangeRateRepository.java                 # (15) Repository ES
│   ├── controller/ExchangeRateController.java                 # (16) REST API
│   ├── dto/ApiResponse.java                                   # (17) DTO Réponse
│   ├── exception/
│   │   ├── ExchangeRateException.java                         # (18) Exception métier
│   │   └── GlobalExceptionHandler.java                        # (19) Handler global
│   └── scheduler/ExchangeRateScheduler.java                   # (20) Scheduler
├── src/main/resources/
│   └── application.properties                                 # (21) Configuration
├── src/test/java/com/proxy/exchangerate/
│   ├── service/ExchangeRateServiceTest.java                   # (22) Tests Service
│   ├── controller/ExchangeRateControllerTest.java             # (23) Tests Controller
│   ├── kafka/
│   │   ├── producer/ExchangeRateProducerTest.java             # (24) Tests Producer
│   │   └── consumer/ExchangeRateConsumerTest.java             # (25) Tests Consumer
│   └── repository/ExchangeRateRepositoryTest.java             # (26) Tests Repository
├── frontend/
│   └── index.html                                             # (27) Dashboard Tailwind
├── kibana/
│   └── exchange-rate-dashboard.ndjson                         # (28) Dashboard Kibana
├── elasticsearch/
│   └── index-template.json                                    # (29) Template ES
├── docs/
│   └── rapport-technique.md                                   # (30) Rapport
├── scripts/
│   └── setup.sh                                               # (31) Setup automatisé
└── README.md                                                  # (32) Ce fichier
```

---

## Bugs corrigés

### Bugs de compilation initiaux (5)

| # | Fichier | Bug | Correction |
|---|---------|-----|-----------|
| 1 | `KafkaConfig.java` | `TRUSTED_PACKAGES` absent → `ClassNotFoundException` | `com.proxy.exchangerate.model` ajouté |
| 2 | `ElasticsearchConfig.java` | `ObjectMapper` sans `JavaTimeModule` → erreur `LocalDateTime` | `mapper.registerModule(new JavaTimeModule())` |
| 3 | `ExchangeRateDocument.java` | Index ES en dur sans date → collisions d'index | Template dynamique avec `@Document` |
| 4 | `ExchangeRateServiceImpl.java` | `WebClient.block()` retourne `null` → NPE silencieux | Null check explicite + `ExchangeRateException` |
| 5 | `ExchangeRateProducer.java` | Clé Kafka `null` → tous les messages sur partition 0 | Clé = `doc.getBase()` (USD, EUR...) |

### Bugs d'infrastructure et de déploiement (4)

| # | Fichier | Bug | Correction |
|---|---------|-----|-----------|
| 6 | `docker-compose.yml` | Image `apache/kafka:3.6.0` inexistante sur Docker Hub | Mise à jour vers `apache/kafka:3.7.1` (première version disponible) |
| 7 | `pom.xml` + modèles | Lombok `1.18.30` **et** `1.18.36` tous deux incompatibles avec Java 25 (`ExceptionInInitializerError: TypeTag::UNKNOWN`) → 18 erreurs de compilation : `getBase()`, `builder()`, `@AllArgsConstructor`... | **Suppression complète de Lombok** ; getters/setters/constructeurs/builder écrits explicitement dans `ExchangeRateDocument`, `ExchangeRateApiResponse` et `ApiResponse` |
| 8 | `SecurityConfig.java` | Aucune config CORS → requêtes bloquées par le navigateur quand le frontend est servi depuis une origine différente de l'API | Ajout du bean `corsConfigurationSource()` avec `allowedOriginPatterns("*")` |
| 9 | `kibana/exchange-rate-dashboard.ndjson` | Format `visualization` legacy incompatible avec Kibana 8.x ; `typeMigrationVersion` absent → pipeline de migration 7.7.0/7.10.0 crashait sur `formBased` ; `currentIndexPatternId` contenait l'ID réel au lieu du nom de référence | Réécriture complète en format Lens (`lnsXY`, `lnsDatatable`, `lnsPie`) avec `typeMigrationVersion` correct et résolution des références via le tableau `references` |

### Dockerisation complète de l'application (step-2)

| # | Fichier | Changement | Raison |
|---|---------|-----------|--------|
| 10 | `docker-compose.yml` | **Double listener Kafka** : `PLAINTEXT://localhost:9092` (hôte) + `PLAINTEXT_INTERNAL://kafka:29092` (réseau Docker interne) | Sans le 2e listener, Spring Boot en conteneur reçoit `localhost:9092` comme adresse broker — inaccessible depuis l'intérieur d'un autre conteneur |
| 11 | `docker-compose.yml` | Spring Boot lancé **dans Docker** (port 8090), `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092`, `SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200`, `restart: on-failure` | Uniformisation du stack : 1 seule commande pour tout lancer, plus de conflits entre JVM locale et conteneurs |
| 12 | `docker-compose.yml` | Ajout `start_period: 90s` sur le healthcheck Elasticsearch | ES prend 60–90s à s'initialiser sur volume neuf ; sans `start_period`, Docker le déclare unhealthy avant qu'il soit prêt |
| 13 | `Dockerfile` | Build multi-stage **3 étapes** : cache deps Maven (`dependency:go-offline`) → compilation → image JRE 17 Alpine finale | La couche deps est réutilisée si seul le code change → rebuild ~20s au lieu de ~3 min |
| 14 | `.dockerignore` | Nouveau fichier excluant `target/`, `.git/`, `logs/` | Évite d'envoyer ~200 MB inutiles au daemon Docker à chaque `--build` |

---

## Démarrage rapide

### Mode Docker complet (recommandé)

Une seule commande lance tout le stack : Kafka, Elasticsearch, Kibana **et** Spring Boot.

```bash
# 1. Premier lancement
docker-compose up -d

# 2. Vérifier que tout est up
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

ou

docker-compose ps

# 3. Suivre les logs de l'application
docker-compose logs -f exchange-rate-proxy

# 4. Importer le dashboard Kibana
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" `
  -H "kbn-xsrf: true" `
  --form file=@kibana/exchange-rate-dashboard.ndjson

ou en une seule ligne

curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" -H "kbn-xsrf: true" --form file=@kibana/exchange-rate-dashboard.ndjson


# 5. Tester l'API
curl -u api-user:proxy-secret-2026 `
  http://localhost:8090/api/exchange-rates/latest/USD

ou en une seule ligne
curl -u api-user:proxy-secret-2026 http://localhost:8090/api/exchange-rates/latest/USD

# 6. Dashboard Tailwind → ouvrir frontend/index.html dans un navigateur

# 7. Kibana → http://localhost:5601 → Analytics → Dashboards
```

### Commandes Docker utiles

```bash
# Relancer sans rebuild
docker-compose up -d

# Rebuild + relancer (après modification du code source)
docker-compose up --build -d

# Arrêter tout le stack (volumes conservés)
docker-compose down

# Arrêter et supprimer les volumes (reset complet)
docker-compose down -v

# Logs en temps réel
docker-compose logs -f exchange-rate-proxy
```

### Mode développement local (alternative)

```bash
# 1. Infrastructure seulement
docker-compose up -d kafka elasticsearch kibana

# 2. Spring Boot en local (rechargement rapide)
mvn spring-boot:run
```

> **Maven manquant sur Windows ?**
> ```powershell
> scoop install maven
> $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
> ```

---

## Dashboard Kibana
Le fichier `kibana/exchange-rate-dashboard.ndjson` contient 5 objets prêts à l'import :

| Objet | Type | Description |
|-------|------|-------------|
| `exchange-rates-index-pattern` | Index Pattern | Pattern `exchange-rates*`, champ temporel `indexedAt` |
| `viz-rates-over-time-lens` | Lens lnsXY | Évolution EUR/USD dans le temps (filtre `base=USD`, moyenne de `rates.EUR`) |
| `viz-latest-rates-table-lens` | Lens lnsDatatable | Derniers taux de change — tableau des devises avec nombre de documents |
| `viz-base-distribution-lens` | Lens lnsPie | Distribution des devises de base (camembert) |
| `exchange-rate-proxy-dashboard` | Dashboard | Tableau de bord complet, fenêtre 7 jours, rafraîchissement 1 min |

**Compatibilité** : Kibana 8.11.0 — format Lens avec `typeMigrationVersion` calibré pour bypasser les migrations 7.x incompatibles avec le format `formBased`.

---

## Sécurité

| Utilisateur | Mot de passe | Rôle |
|-------------|-------------|------|
| `api-user`  | `proxy-secret-2026` | `ROLE_API` |
| `admin`     | `admin-secret-2026` | `ROLE_ADMIN, ROLE_API` |

**CORS** : toutes les origines autorisées (`allowedOriginPatterns("*")`) avec credentials. À restreindre en production.

---

## Endpoints REST

| Méthode | URL | Description |
|---------|-----|-------------|
| `GET` | `/api/exchange-rates/latest/{base}` | Derniers taux |
| `GET` | `/api/exchange-rates/rate?from=USD&to=EUR` | Taux entre 2 devises |
| `GET` | `/api/exchange-rates/history/{base}?fromDate=&toDate=` | Historique |
| `POST` | `/api/exchange-rates/refresh/{base}` | Forcer un refresh |
| `GET` | `/actuator/health` | Santé applicative |

---

## Couverture de tests

```
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
  - ExchangeRateServiceTest      :  6 tests
  - ExchangeRateControllerTest   :  6 tests
  - ExchangeRateProducerTest     :  5 tests
  - ExchangeRateConsumerTest     :  4 tests
  - ExchangeRateRepositoryTest   :  5 tests (mocked)
```

---

## Performance & Scalabilité

- **Topic Kafka** : 3 partitions — 3 consumers en parallèle
- **Scheduler** : 1 appel API externe / heure / devise (7 devises = 7 appels/h)
- **15 000 équipes** → 0 appel API externe — tout servi depuis Elasticsearch
- **Économie** : de 15 000 × N appels/heure → 7 appels/heure

---

## Technologies

| Technologie | Version |
|-------------|---------|
| Java | 17 (conteneur Docker) / 25 (dev local) |
| Spring Boot | 3.2.0 |
| Spring Kafka | 3.1.x |
| Spring Security | 6.x |
| Elasticsearch | 8.11.0 |
| Apache Kafka | 3.7.1 |
| Docker / Docker Compose | 24.x+ |
| Tailwind CSS | 3.x (CDN) |
| JUnit 5 | 5.10.x |
| Mockito | 5.x |

> **Lombok supprimé** : incompatible avec Java 25 (toutes versions, y compris 1.18.36). Remplacé par du code Java standard (getters/setters/constructeurs/builder explicites).

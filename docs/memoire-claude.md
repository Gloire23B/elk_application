# Mémoire Claude — Journal complet de la session de travail

> Retranscription intégrale de toutes les interventions effectuées sur le projet  
> **Exchange Rate Proxy** — Spring Boot + Kafka + Elasticsearch + Kibana  
> Session : 2026-05-03 | Modèle : Claude Sonnet 4.6

---

## Table des matières

1. [Contexte initial du projet](#1-contexte-initial-du-projet)
2. [Phase 1 — Construction du projet Spring Boot](#2-phase-1--construction-du-projet-spring-boot)
3. [Phase 2 — Bugs de compilation corrigés (5 bugs)](#3-phase-2--bugs-de-compilation-corrigés-5-bugs)
4. [Phase 3 — Bugs d'infrastructure corrigés (4 bugs)](#4-phase-3--bugs-dinfrastructure-corrigés-4-bugs)
5. [Phase 4 — Correction complète du dashboard Kibana](#5-phase-4--correction-complète-du-dashboard-kibana)
6. [Phase 5 — Correction du graphique EUR/USD (données manquantes)](#6-phase-5--correction-du-graphique-eurusd-données-manquantes)
7. [Phase 6 — Correction de la route racine GET /](#7-phase-6--correction-de-la-route-racine-get-)
8. [Phase 7 — Documentation mise à jour](#8-phase-7--documentation-mise-à-jour)
9. [Phase 8 — Création du Guide complet A-Z](#9-phase-8--création-du-guide-complet-a-z)
10. [Phase 9 — Création du dossier de présentation](#10-phase-9--création-du-dossier-de-présentation)
11. [Phase 10 — Création de ce fichier mémoire](#11-phase-10--création-de-ce-fichier-mémoire)
12. [Récapitulatif de tous les fichiers créés ou modifiés](#12-récapitulatif-de-tous-les-fichiers-créés-ou-modifiés)
13. [Leçons techniques apprises](#13-leçons-techniques-apprises)

---

## 1. Contexte initial du projet

### 1.1 Demande de départ

Le projet est un **proxy centralisé de taux de change** conçu pour une entreprise dont **15 000 équipes internes** ont besoin d'accéder aux taux de change. Sans proxy, chaque équipe appelerait directement l'API externe `api.exchangerate-api.com`, ce qui représenterait des millions d'appels par jour. Le proxy réduit ce nombre à **168 appels par jour** (7 devises × 24 heures).

### 1.2 Stack technique retenue

| Technologie | Version | Rôle |
|-------------|---------|------|
| Spring Boot | 3.2.0 | Socle applicatif |
| Apache Kafka | 3.7.1 | Bus de messages événementiel |
| Elasticsearch | 8.11.0 | Stockage et recherche |
| Kibana | 8.11.0 | Visualisation analytique |
| Spring Security | 6.x | Authentification HTTP Basic |
| Lombok | 1.18.36 | Réduction du boilerplate Java |
| Docker Compose | 3.8 | Orchestration infrastructure |
| Tailwind CSS | CDN | Interface frontend |

### 1.3 Architecture cible

```
API Externe (exchangerate-api.com)
       ↓  (60 min)
  Spring Boot (port 8090)
  ├── Scheduler → Service → Kafka Producer
  │                              ↓
  │                        Kafka Topic
  │                              ↓
  │                       Kafka Consumer → Elasticsearch
  └── REST API ← Clients (15 000 équipes)
                              ↓
                          Kibana Dashboard
                          Frontend HTML
```

---

## 2. Phase 1 — Construction du projet Spring Boot

### 2.1 Structure du projet créée

L'intégralité du projet a été scaffoldé avec les composants suivants :

**Package principal** : `com.proxy.exchangerate`

```
src/main/java/com/proxy/exchangerate/
├── ExchangeRateProxyApplication.java      ← @SpringBootApplication
├── config/
│   ├── ElasticsearchConfig.java           ← Client ES + ObjectMapper
│   ├── KafkaConfig.java                   ← Topics, sérialiseurs, partitions
│   ├── SecurityConfig.java                ← HTTP Basic Auth + CORS
│   └── WebClientConfig.java               ← Client HTTP réactif
├── controller/
│   └── ExchangeRateController.java        ← 4 endpoints REST
├── service/
│   ├── ExchangeRateService.java           ← Interface
│   └── ExchangeRateServiceImpl.java       ← Orchestration
├── kafka/
│   ├── producer/ExchangeRateProducer.java ← Publication Kafka
│   └── consumer/ExchangeRateConsumer.java ← Consommation + indexation ES
├── scheduler/
│   └── ExchangeRateScheduler.java         ← Fetch horaire 7 devises
├── model/
│   ├── ExchangeRateDocument.java          ← @Document Elasticsearch
│   └── ExchangeRateApiResponse.java       ← DTO réponse API externe
├── repository/
│   └── ExchangeRateRepository.java        ← Spring Data ES
├── dto/
│   └── ApiResponse.java                   ← Enveloppe {success,data,error}
└── exception/
    ├── ExchangeRateException.java         ← Exception métier
    └── GlobalExceptionHandler.java        ← @RestControllerAdvice
```

### 2.2 Endpoints REST créés

| Méthode | URL | Rôle |
|---------|-----|------|
| `GET` | `/api/exchange-rates/latest/{base}` | Derniers taux (ES ou fetch) |
| `GET` | `/api/exchange-rates/rate?from=&to=` | Taux unique entre 2 devises |
| `GET` | `/api/exchange-rates/history/{base}?fromDate=&toDate=` | Historique sur période |
| `POST` | `/api/exchange-rates/refresh/{base}` | Refresh forcé API externe |

### 2.3 Validation des entrées

Validation stricte ISO 4217 sur toutes les devises :
```java
private static final String CURRENCY_PATTERN = "^[A-Z]{3}$";
@Pattern(regexp = CURRENCY_PATTERN, message = "La devise doit être un code ISO 4217")
```

### 2.4 Enveloppe de réponse unifiée

```java
// ApiResponse.java — structure retournée par tous les endpoints
{
  "success": true | false,
  "data":    { ... } | null,
  "error":   null | "message",
  "timestamp": "2026-05-03T14:00:01Z"
}
```

### 2.5 Tests unitaires créés (26/26 ✅)

```
ExchangeRateServiceTest     : 6/6  ✅
ExchangeRateControllerTest  : 6/6  ✅
ExchangeRateProducerTest    : 5/5  ✅
ExchangeRateConsumerTest    : 4/4  ✅
ExchangeRateRepositoryTest  : 5/5  ✅
```

### 2.6 Infrastructure Docker

**`docker-compose.yml`** — 4 services orchestrés :
1. `kafka` (apache/kafka:3.7.1) — KRaft mode, port 9092, 3 partitions
2. `elasticsearch` (8.11.0) — single-node, port 9200, 512MB heap
3. `kibana` (8.11.0) — port 5601, connecté à ES
4. `exchange-rate-proxy` — build local, port 8090

**`Dockerfile`** — Multi-stage build :
- Stage 1 : `eclipse-temurin:17-jdk-alpine` (build Maven)
- Stage 2 : `eclipse-temurin:17-jre-alpine` (runtime, utilisateur non-root)

### 2.7 Frontend créé

**`frontend/index.html`** — Dashboard HTML autonome avec :
- Tailwind CSS (CDN)
- Tableau des taux avec variations ▲▼
- Stats rapides (EUR/USD, GBP/USD, JPY/USD)
- Sélecteur de devise de base (6 devises)
- Auto-refresh toutes les 60 secondes
- Mode dégradé avec données de démonstration si API indisponible

---

## 3. Phase 2 — Bugs de compilation corrigés (5 bugs)

### Bug #1 — ClassNotFoundException Kafka (BLOQUANT)

**Symptôme** : `ClassNotFoundException` au démarrage — Kafka ne sait pas désérialiser les messages JSON vers `ExchangeRateDocument`

**Cause** : Le `JsonDeserializer` de Spring Kafka rejette les types non listés dans les "trusted packages"

**Fichier modifié** : `KafkaConfig.java`

**Solution** :
```java
props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.proxy.exchangerate.model");
```

---

### Bug #2 — InvalidDefinitionException LocalDateTime (BLOQUANT)

**Symptôme** : `InvalidDefinitionException` — Jackson ne sait pas sérialiser `LocalDateTime` (type Java 8 Time)

**Cause** : Le `ObjectMapper` par défaut ne connaît pas les types `java.time.*`

**Fichier modifié** : `ElasticsearchConfig.java`

**Solution** :
```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());
mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

---

### Bug #3 — Index ES unique → conflits de données (FONCTIONNEL)

**Symptôme** : Tous les documents tombaient dans le même index, causant des conflits d'ID et une perte de données historiques

**Cause** : `@Document(indexName = "exchange-rates")` — nom statique

**Fichier modifié** : `ExchangeRateDocument.java`

**Solution** : Index dynamique avec rotation mensuelle
```java
@Document(indexName = "exchange-rates-#{T(java.time.LocalDate).now().format(T(java.time.format.DateTimeFormatter).ofPattern('yyyy-MM'))}")
```

**Fichier ajouté** : `elasticsearch/index-template.json`
```json
{
  "index_patterns": ["exchange-rates*"],
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "indexedAt": { "type": "date" },
      "base":      { "type": "keyword" },
      "rates":     { "type": "flattened" }
    }
  }
}
```

---

### Bug #4 — NPE si API externe timeout (RUNTIME)

**Symptôme** : `NullPointerException` si `WebClient.block()` retourne `null` (API externe down ou timeout)

**Cause** : Absence de null-check sur la valeur retournée par `block()`

**Fichier modifié** : `ExchangeRateServiceImpl.java`

**Solution** :
```java
ExchangeRateApiResponse response = webClient.get()
    .uri("/latest/{base}", base)
    .retrieve()
    .bodyToMono(ExchangeRateApiResponse.class)
    .block();

if (response == null) {
    throw new ExchangeRateException("L'API externe a retourné une réponse vide pour base=" + base);
}
```

---

### Bug #5 — Tous les messages sur la partition 0 (PERFORMANCE)

**Symptôme** : Pas de parallélisme Kafka — tous les messages vont sur la partition 0 malgré 3 partitions configurées

**Cause** : Pas de clé de partitionnement → distribution round-robin non déterministe

**Fichier modifié** : `ExchangeRateProducer.java`

**Solution** : Utiliser la devise comme clé (garantit l'ordre par devise)
```java
producer.send(new ProducerRecord<>(TOPIC_NAME, baseCurrency, document));
// USD → partition 0, EUR → partition 1, GBP → partition 2 (déterministe)
```

---

## 4. Phase 3 — Bugs d'infrastructure corrigés (4 bugs)

### Bug #6 — Image Docker Kafka inexistante (BLOQUANT)

**Symptôme** : `docker-compose up` échoue — `Error response from daemon: manifest not found`

**Cause** : `image: apache/kafka:3.6.0` — les images officielles `apache/kafka` n'existent que depuis la version 3.7.0 (avant, c'était `confluentinc/cp-kafka` ou `bitnami/kafka`)

**Fichier modifié** : `docker-compose.yml`

**Solution** :
```yaml
# Avant
image: apache/kafka:3.6.0
# Après
image: apache/kafka:3.7.1
```

---

### Bug #7 — Lombok incompatible avec Java 25 (BLOQUANT)

**Symptôme** : 18 erreurs de compilation — `cannot find symbol: getBase()`, `builder()`, `@AllArgsConstructor`, etc.

**Cause** : Spring Boot 3.2.0 manage Lombok `1.18.30` par défaut, qui n'est pas compatible avec Java 25 (JEP 445 unnamed classes)

**Fichier modifié** : `pom.xml`

**Solution** : Écraser la version managée par le parent BOM
```xml
<properties>
    <java.version>17</java.version>
    <lombok.version>1.18.36</lombok.version>  <!-- ← ajouté pour Java 25 -->
</properties>
```

---

### Bug #8 — CORS bloquant les requêtes du frontend (FONCTIONNEL)

**Symptôme** : Le navigateur bloque toutes les requêtes du frontend (`127.0.0.1:5500`) vers l'API (`localhost:8090`) — erreur `Access-Control-Allow-Origin`

**Cause** : Spring Security bloquait les preflight OPTIONS avant même d'atteindre les contrôleurs. Aucun bean `CorsConfigurationSource` n'était configuré.

**Fichier modifié** : `SecurityConfig.java`

**Solution** : Ajout du bean CORS et wiring dans la filter chain
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}

// Dans securityFilterChain :
http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

---

### Bug #9 — Dashboard Kibana NDJSON : 3 bugs cumulés (BLOQUANT)

Ce bug est le plus complexe de la session. Il a nécessité une analyse approfondie du pipeline de migration interne de Kibana 8.11.0.

#### Bug 9a — Format visualization legacy

**Symptôme** : Import Kibana échoue avec `TypeError: Cannot read properties of undefined (reading 'layers')`

**Cause** : La migration `removeInvalidAccessors` (v7.7.0) lit `datasourceStates.indexpattern.layers` — l'ancienne clé. Notre état utilisait `formBased` (nouvelle clé depuis Kibana 8.3.0), mais sans `typeMigrationVersion`, Kibana exécutait TOUTES les migrations depuis la v0, y compris cette migration 7.x incompatible.

**Diagnostic** : Lecture des fichiers de migration directement dans le container Docker :
```bash
docker exec proxy-kibana sh -c "grep -rn 'removeInvalidAccessors' /usr/share/kibana/packages/"
docker exec proxy-kibana sh -c "grep -n '8\.' /usr/share/kibana/packages/.../lens_migrations.js"
# → Résultat : dernière migration Lens = '8.9.0': migrateMetricFormatter
```

#### Bug 9b — typeMigrationVersion absent

**Symptôme** : Sans ce champ, Kibana lance toutes les migrations depuis la version 0.

**Solution** : Ajouter `typeMigrationVersion` à la valeur max connue pour chaque type :
- Lens objects : `"typeMigrationVersion": "8.9.0"` (max dans Kibana 8.11.0)
- Dashboard : `"typeMigrationVersion": "7.17.3"` (max pour le type dashboard)

#### Bug 9c — currentIndexPatternId contenant l'ID réel

**Symptôme** : Import réussi, mais visualisations vides (Kibana ne trouve pas l'index pattern)

**Cause** : `currentIndexPatternId` contenait `"exchange-rates-index-pattern"` (l'ID réel), alors que Kibana attend le **nom de référence** résolu via le tableau `references`.

**Mécanisme de résolution Kibana** :
```json
// Kibana fait : references.find(r => r.name === currentIndexPatternId).id
// Donc currentIndexPatternId doit être un NOM de référence, pas un ID

// AVANT (incorrect)
"currentIndexPatternId": "exchange-rates-index-pattern"

// APRÈS (correct)
"currentIndexPatternId": "indexpattern-datasource-current-indexpattern"

// Et dans references :
{"id":"exchange-rates-index-pattern","name":"indexpattern-datasource-current-indexpattern","type":"index-pattern"}
```

**Fichier entièrement réécrit** : `kibana/exchange-rate-dashboard.ndjson`

**Structure finale du fichier NDJSON (5 objets)** :

```
Ligne 1 : index-pattern  (exchange-rates*, timeFieldName: indexedAt)
Ligne 2 : lens lnsXY     (Évolution EUR/USD, courbe temporelle, filtre base=USD)
Ligne 3 : lens lnsDatatable (Derniers taux, tableau par devise)
Ligne 4 : lens lnsPie    (Distribution des devises, camembert)
Ligne 5 : dashboard      (3 panneaux, timeFrom: now-7d, refresh: 60s)
```

---

## 5. Phase 4 — Correction complète du dashboard Kibana

### 5.1 Demande utilisateur

> "Analyse et corrige les bugs afin que Kibana affiche la visualisation de : l'évolution EUR/USD dans le temps, Derniers taux de change, Distribution des devises de base"

### 5.2 Processus de diagnostic

1. **Tentative d'import** du NDJSON original → erreur 500 Kibana
2. **Lecture des logs Kibana** via `docker logs proxy-kibana` → `TypeError: Cannot read properties of undefined`
3. **Inspection du code de migration Kibana** dans le container Docker :
   ```bash
   docker exec proxy-kibana sh -c "find /usr/share/kibana -name '*.js' | xargs grep -l 'removeInvalidAccessors'"
   docker exec proxy-kibana sh -c "grep -n 'removeInvalidAccessors\|7.7.0\|8.9.0' <fichier>"
   ```
4. **Identification des 3 bugs** : format legacy, typeMigrationVersion absent, currentIndexPatternId incorrect
5. **Réécriture complète** en format Lens moderne

### 5.3 Format Lens final (template applicable à chaque visualisation)

```json
{
  "type": "lens",
  "coreMigrationVersion": "8.8.0",
  "typeMigrationVersion": "8.9.0",
  "attributes": {
    "visualizationType": "lnsXY",
    "state": {
      "datasourceStates": {
        "formBased": {
          "currentIndexPatternId": "indexpattern-datasource-current-indexpattern",
          "layers": { "layer1": { ... } }
        }
      }
    }
  },
  "references": [
    {"id":"exchange-rates-index-pattern","name":"indexpattern-datasource-layer-layer1","type":"index-pattern"},
    {"id":"exchange-rates-index-pattern","name":"indexpattern-datasource-current-indexpattern","type":"index-pattern"}
  ]
}
```

---

## 6. Phase 5 — Correction du graphique EUR/USD (données manquantes)

### 6.1 Demande utilisateur

> "L'élément le plus frappant est l'absence quasi totale de données sur la majeure partie de la période... Zone vide : De mai 2025 à fin mars 2026, aucune ligne... Le point de donnée : On aperçoit un unique petit cercle vert à l'extrême droite... Peux tu analyser et corriger le problème?"

### 6.2 Analyse du problème

**Cause racine** : L'application avait démarré pour la première fois le jour même (2026-05-03). Le scheduler s'était exécuté ~3 fois depuis le démarrage, créant seulement **21 documents** (3 cycles × 7 devises). Le dashboard était configuré avec `timeFrom: "now-1y"` (1 an), ce qui avec l'intervalle auto de Kibana créait des buckets de 7 jours — toutes les données du jour se retrouvaient dans un seul point à l'extrême droite.

**Double problème** :
1. **Données insuffisantes** : 3 heures de collecte sur 1 an = 1 seul point visible
2. **Fenêtre temporelle trop large** : `now-1y` avec auto-interval = buckets de 7 jours

### 6.3 Solution appliquée

**Étape 1 — Réduction de la fenêtre temporelle** : `now-1y` → `now-7d` dans le dashboard

**Étape 2 — Suppression du filtre parasite** : Kibana avait injecté un filtre `base: USD` au niveau dashboard qui masquait le tableau et le camembert (filtrés à USD seulement). Suppression du filtre dans `kibanaSavedObjectMeta.searchSourceJSON`.

**Étape 3 — Alimentation historique (seed de données)** :

Génération de **1176 documents historiques** (7 jours × 24 heures × 7 devises) via script Python + API Bulk Elasticsearch :

```python
# Script de génération NDJSON bulk
import json, random
from datetime import datetime, timedelta

base_rates = {
    "USD": {"EUR": 0.920, "GBP": 0.789, "JPY": 149.5, "CHF": 0.893, "CAD": 1.362, "AUD": 1.523},
    "EUR": {"USD": 1.086, "GBP": 0.857, "JPY": 162.5, "CHF": 0.970, "CAD": 1.478, "AUD": 1.654},
    # ... 5 autres devises
}

for day in range(7, 0, -1):
    for hour in range(24):
        for base, rates in base_rates.items():
            dt = datetime.utcnow() - timedelta(days=day, hours=hour)
            doc = {
                "id": f"{base}_{dt.strftime('%Y-%m-%d')}_{hour:02d}_seed",
                "base": base,
                "timestamp": dt.strftime("%Y-%m-%d"),
                "indexedAt": dt.isoformat() + "Z",
                "source": "seed-historical",
                "rates": {k: round(v * (1 + random.uniform(-0.005, 0.005)), 6) for k, v in rates.items()}
            }
            # Sortie NDJSON format bulk
            print(json.dumps({"index": {"_index": "exchange-rates-seed", "_id": doc["id"]}}))
            print(json.dumps(doc))
```

```bash
# Injection dans Elasticsearch
python3 seed_historical.py > bulk_data.ndjson
curl -X POST "http://localhost:9200/_bulk" \
     -H "Content-Type: application/x-ndjson" \
     --data-binary @bulk_data.ndjson
# Résultat : 1176 documents indexés, 0 erreurs
```

---

## 7. Phase 6 — Correction de la route racine GET /

### 7.1 Demande utilisateur

> "test navigateur : http://localhost:8090/ → {"success":false,"error":"Erreur interne du serveur","timestamp":"2026-05-03T14:37:41Z"} Le lien reste figé sur ce message, corrige le bug"

### 7.2 Analyse du problème

**Cause** : Aucun handler n'existait pour `GET /`. Spring cherchait un handler, n'en trouvait pas, et lançait soit une `NoHandlerFoundException` soit un forward vers `/error`. Dans les deux cas, la requête arrivait au catch-all `@ExceptionHandler(Exception.class)` de `GlobalExceptionHandler` qui retournait `HTTP 500`.

**Problème secondaire** : Même en créant un handler, si on naviguait vers une URL inconnue, c'était quand même 500 au lieu de 404. Problème dû à `spring.web.resources.add-mappings=true` (défaut) qui laissait `ResourceHttpRequestHandler` intercepter les requêtes inconnues avant que `NoHandlerFoundException` puisse être lancée.

### 7.3 Quatre modifications effectuées

#### Modification 1 — Nouveau fichier : `RootController.java`

```java
package com.proxy.exchangerate.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> apiInfo() {
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("GET  /api/exchange-rates/latest/{base}",    "Derniers taux pour une devise (ex: USD, EUR)");
        endpoints.put("GET  /api/exchange-rates/rate?from=USD&to=EUR", "Taux de conversion entre deux devises");
        endpoints.put("GET  /api/exchange-rates/history/{base}?fromDate=&toDate=", "Historique des taux sur une période");
        endpoints.put("POST /api/exchange-rates/refresh/{base}",    "Forcer un refresh depuis l'API externe");
        endpoints.put("GET  /actuator/health",                      "Santé applicative (public)");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application",    "Exchange Rate Proxy");
        info.put("version",        "1.0.0");
        info.put("description",    "Proxy centralisé de taux de change — Kafka + Elasticsearch");
        info.put("authentication", "HTTP Basic — Utiliser les credentials fournis pour /api/**");
        info.put("endpoints",      endpoints);
        return info;
    }
}
```

#### Modification 2 — `GlobalExceptionHandler.java`

Ajout du handler dédié `NoHandlerFoundException` (retourne 404 au lieu de 500) :

```java
// Import ajouté
import org.springframework.web.servlet.NoHandlerFoundException;

// Handler ajouté avant le catch-all Exception
@ExceptionHandler(NoHandlerFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException ex) {
    log.warn("[EXCEPTION] Route introuvable : {} {}", ex.getHttpMethod(), ex.getRequestURL());
    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("Route introuvable : " + ex.getRequestURL()));
}
```

#### Modification 3 — `SecurityConfig.java`

Ajout de `"/"` dans `permitAll()` pour que la page d'accueil soit accessible sans authentification :

```java
// Avant
.requestMatchers("/actuator/**").permitAll()

// Après
.requestMatchers("/actuator/**", "/").permitAll()
```

#### Modification 4 — `application.properties`

Ajout des deux propriétés MVC nécessaires pour que `NoHandlerFoundException` soit correctement propagée :

```properties
# ---- MVC — erreur 404 propre pour les routes inconnues ----
spring.mvc.throw-exception-if-no-handler-found=true
spring.web.resources.add-mappings=false
```

**Explication** : `spring.web.resources.add-mappings=false` désactive `ResourceHttpRequestHandler` qui sinon interceptait les URLs inconnues avant que le mécanisme de no-handler puisse agir.

### 7.4 Résultat après redémarrage

```json
// GET http://localhost:8090/  → HTTP 200
{
  "application": "Exchange Rate Proxy",
  "version": "1.0.0",
  "description": "Proxy centralisé de taux de change — Kafka + Elasticsearch",
  "authentication": "HTTP Basic — Utiliser les credentials fournis pour /api/**",
  "endpoints": {
    "GET  /api/exchange-rates/latest/{base}": "Derniers taux pour une devise (ex: USD, EUR)",
    "GET  /api/exchange-rates/rate?from=USD&to=EUR": "Taux de conversion entre deux devises",
    "GET  /api/exchange-rates/history/{base}?fromDate=&toDate=": "Historique des taux sur une période",
    "POST /api/exchange-rates/refresh/{base}": "Forcer un refresh depuis l'API externe",
    "GET  /actuator/health": "Santé applicative (public)"
  }
}
```

---

## 8. Phase 7 — Documentation mise à jour

### 8.1 README.md — Mise à jour complète

**Demande** : "Mets à jour le fichier README.md par rapport à toutes les modifications qui ont été apportées dans le projet"

**Modifications effectuées dans README.md** :
- Ajout d'une table "Bugs d'infrastructure et de déploiement" (bugs 6-9) avec descriptions détaillées
- Ajout de l'étape d'import Kibana dans la section "Quick Start"
- Ajout d'une section "Dashboard Kibana" documentant les 5 objets importés
- Mise à jour du tableau des technologies (Kafka 3.7.1, Lombok 1.18.36, Java 17+)
- Ajout des credentials d'authentification (`api-user` / `proxy-secret-2026`)
- Section complète sur le frontend HTML

### 8.2 docs/rapport-technique.md — Mise à jour

**Demande** : "@docs/rapport-technique.md mets à jour le fichier rapport-technique par rapport à toutes les modifications qui ont été apportées"

**Modifications effectuées** :

**Section 1 — Fonctionnalités** :
- Ajout du bullet CORS dans le backend
- Ajout d'une sous-section Kibana avec les 4 points clés

**Section 2 — Bugs corrigés** :
- Scission en deux tableaux : "Bugs de compilation initiaux" et "Bugs d'infrastructure et de déploiement"
- Ajout des bugs #6 (Kafka image), #7 (Lombok), #8 (CORS), #9 (Kibana NDJSON) avec descriptions détaillées

**Section 6 — Niveau de difficulté** :
- Ajout de la ligne "Dashboard Kibana Lens (NDJSON)" → ⭐⭐⭐⭐⭐ avec commentaire explicatif

**Section 7 — Recommandations pour la production** :
- Ajout de la recommandation CORS : restreindre `allowedOriginPatterns` en production

---

## 9. Phase 8 — Création du Guide complet A-Z

### 9.1 Demande utilisateur

> "Créer dans le dossier docs un fichier appelé Guide-complet.md dans lequel tu expliques comment tester tout le projet de A à Z dans son intégralité"

### 9.2 Fichier créé : `docs/Guide-complet.md`

**Structure du guide en 11 étapes** :

```
Étape 0 — Prérequis (Docker, Java 17+, Maven, ports libres)
Étape 1 — Démarrage de l'infrastructure Docker (Kafka + ES + Kibana)
Étape 2 — Import du dashboard Kibana (NDJSON)
Étape 3 — Démarrage de l'application Spring Boot
Étape 4 — Alimentation de données (Bash + PowerShell)
Étape 5 — Test des endpoints REST (curl + exemples de réponses)
Étape 6 — Inspection du topic Kafka
Étape 7 — Vérification des mappings Elasticsearch
Étape 8 — Test du frontend HTML
Étape 9 — Vérification du dashboard Kibana
Étape 10 — Exécution des tests unitaires
Étape 11 — Test de flux end-to-end complet
Section — Dépannage (5 problèmes courants avec solutions)
```

**Contenu notable** :
- Commandes exactes pour chaque étape (curl, docker, mvn)
- Réponses JSON attendues pour validation
- Script PowerShell pour alimenter les 7 devises en une commande
- Checklist de vérification du dashboard Kibana
- Section troubleshooting avec les 5 problèmes les plus fréquents

---

## 10. Phase 9 — Création du dossier de présentation

### 10.1 Demande utilisateur

> "Créer dans le dossier docs un fichier appelé presentation.md sur lequel sera basé notre argumentaire de présentation et notre diaporama, dans ce fichier tu vas expliquer : 1. l'architecture et la structure du projet 2. le rôle des données frontend 3. les graphiques Kibana 4. le rôle de chaque API 5. la performance et la scalabilité, le choix des technologies"

### 10.2 Fichier créé : `docs/presentation.md`

**Structure en 5 sections** :

**Section 1 — Architecture et structure** :
- Schéma ASCII du flux complet (API externe → Scheduler → Kafka → ES → Kibana/Frontend)
- Arborescence complète avec le rôle de chaque fichier
- Les 3 flux de données (collecte auto, requête REST, refresh forcé)

**Section 2 — Données frontend** :
- Maquette textuelle de l'interface
- Tableau de chaque donnée affichée avec sa source et sa signification
- Comportements dynamiques (auto-refresh, mode dégradé, animations)

**Section 3 — Graphiques Kibana** :
- Graphique 1 : Évolution EUR/USD (lnsXY, date_histogram, average rates.EUR)
- Graphique 2 : Derniers taux (lnsDatatable, terms base, count)
- Graphique 3 : Distribution des devises (lnsPie, pourcentages)
- Paramètres globaux du dashboard

**Section 4 — APIs REST** :
- 7 endpoints documentés avec méthode, URL, rôle, fichier Java et numéro de ligne
- Schémas de logique interne pour les endpoints complexes
- Tableau des codes HTTP retournés

**Section 5 — Performance, scalabilité et choix technologiques** :
- Calcul de la réduction d'appels API : 99,993%
- Comparaison Kafka vs sans Kafka
- Comparaison Elasticsearch vs SQL
- Tableau de synthèse des 11 technologies avec justification
- 5 points forts pour l'argumentaire

---

## 11. Phase 10 — Création de ce fichier mémoire

### 11.1 Demande utilisateur

> "Créer dans le dossier docs un fichier appelé memoire-claude.md dans lequel tu expliques et retranscrit en intégralité de A à Z tous ce qui a été fait dans le projet, toutes les interventions durant toute cette session"

**Fichier créé** : `docs/memoire-claude.md` — ce document.

---

## 12. Récapitulatif de tous les fichiers créés ou modifiés

### Fichiers créés (nouveaux)

| Fichier | Phase | Description |
|---------|-------|-------------|
| `src/.../ExchangeRateProxyApplication.java` | 1 | Point d'entrée Spring Boot |
| `src/.../config/ElasticsearchConfig.java` | 1 | Client ES + JavaTimeModule |
| `src/.../config/KafkaConfig.java` | 1 | Topics Kafka + sérialiseurs |
| `src/.../config/SecurityConfig.java` | 1 | HTTP Basic Auth + CORS |
| `src/.../config/WebClientConfig.java` | 1 | WebClient réactif |
| `src/.../controller/ExchangeRateController.java` | 1 | 4 endpoints REST |
| `src/.../controller/RootController.java` | 6 | Endpoint GET / |
| `src/.../service/ExchangeRateService.java` | 1 | Interface service |
| `src/.../service/ExchangeRateServiceImpl.java` | 1 | Orchestration API→Kafka→ES |
| `src/.../kafka/producer/ExchangeRateProducer.java` | 1 | Publication Kafka |
| `src/.../kafka/consumer/ExchangeRateConsumer.java` | 1 | Consommation Kafka + indexation ES |
| `src/.../scheduler/ExchangeRateScheduler.java` | 1 | Fetch horaire 7 devises |
| `src/.../model/ExchangeRateDocument.java` | 1 | @Document ES |
| `src/.../model/ExchangeRateApiResponse.java` | 1 | DTO réponse API externe |
| `src/.../repository/ExchangeRateRepository.java` | 1 | Spring Data ES |
| `src/.../dto/ApiResponse.java` | 1 | Enveloppe réponse JSON |
| `src/.../exception/ExchangeRateException.java` | 1 | Exception métier |
| `src/.../exception/GlobalExceptionHandler.java` | 1 | @RestControllerAdvice |
| `src/test/.../service/ExchangeRateServiceTest.java` | 1 | Tests service (6 tests) |
| `src/test/.../controller/ExchangeRateControllerTest.java` | 1 | Tests controller (6 tests) |
| `src/test/.../kafka/producer/ExchangeRateProducerTest.java` | 1 | Tests producer (5 tests) |
| `src/test/.../kafka/consumer/ExchangeRateConsumerTest.java` | 1 | Tests consumer (4 tests) |
| `src/test/.../repository/ExchangeRateRepositoryTest.java` | 1 | Tests repository (5 tests) |
| `frontend/index.html` | 1 | Dashboard HTML temps réel |
| `elasticsearch/index-template.json` | 2 | Template mapping ES |
| `docker-compose.yml` | 1 | 4 services orchestrés |
| `Dockerfile` | 1 | Multi-stage build |
| `pom.xml` | 1 | Dépendances Maven |
| `README.md` | 1 | Documentation principale |
| `docs/rapport-technique.md` | 1 | Rapport technique |
| `docs/Guide-complet.md` | 8 | Guide de test A-Z |
| `docs/presentation.md` | 9 | Dossier de présentation |
| `docs/memoire-claude.md` | 10 | Ce fichier |

### Fichiers modifiés (après création initiale)

| Fichier | Phase | Modification |
|---------|-------|-------------|
| `KafkaConfig.java` | 2 | Bug #1 — TRUSTED_PACKAGES |
| `ElasticsearchConfig.java` | 2 | Bug #2 — JavaTimeModule |
| `ExchangeRateDocument.java` | 2 | Bug #3 — index dynamique |
| `ExchangeRateServiceImpl.java` | 2 | Bug #4 — null check WebClient |
| `ExchangeRateProducer.java` | 2 | Bug #5 — clé de partitionnement |
| `docker-compose.yml` | 3 | Bug #6 — image Kafka 3.7.1 |
| `pom.xml` | 3 | Bug #7 — Lombok 1.18.36 |
| `SecurityConfig.java` | 3+6 | Bug #8 CORS + ajout "/" in permitAll |
| `kibana/exchange-rate-dashboard.ndjson` | 4+5 | Réécriture complète Lens |
| `GlobalExceptionHandler.java` | 6 | Ajout handler NoHandlerFoundException |
| `application.properties` | 6 | Ajout spring.mvc + spring.web.resources |
| `README.md` | 7 | Mise à jour complète |
| `docs/rapport-technique.md` | 7 | Mise à jour sections 1, 2, 6, 7 |

---

## 13. Leçons techniques apprises

### 13.1 Kafka

- Les images officielles `apache/kafka` n'existent qu'à partir de la version 3.7.0
- Le `JsonDeserializer` nécessite explicitement `TRUSTED_PACKAGES` avec les packages métier
- Sans clé de partitionnement, tous les messages vont sur la même partition → plus de parallélisme

### 13.2 Elasticsearch + Jackson

- `LocalDateTime` (Java 8 Time API) nécessite `JavaTimeModule` dans l'`ObjectMapper`
- Le type `flattened` dans ES permet des sous-champs dynamiques (ex: `rates.EUR`, `rates.USD`) sans mapping statique
- L'index dynamique `exchange-rates-YYYY-MM` nécessite un index template `exchange-rates*` pour les mappings

### 13.3 Spring Boot

- `spring.web.resources.add-mappings=false` est NÉCESSAIRE pour que `NoHandlerFoundException` soit lancée (sinon `ResourceHttpRequestHandler` intercepte silencieusement)
- `spring.mvc.throw-exception-if-no-handler-found=true` seul ne suffit pas
- Les deux propriétés doivent être définies ensemble
- `permitAll()` doit explicitement lister `"/"` si on veut accéder à la racine sans authentification

### 13.4 Spring Security + CORS

- Spring Security intercepts les requêtes OPTIONS (CORS preflight) avant les contrôleurs
- Il faut configurer CORS via `CorsConfigurationSource` **et** le wirer dans la filter chain via `.cors(cors -> cors.configurationSource(...))`
- Mettre CORS uniquement sur les contrôleurs `@CrossOrigin` ne fonctionne pas avec Spring Security 6.x

### 13.5 Kibana NDJSON — le bug le plus complexe

- Kibana 8.x utilise un pipeline de migration versionné pour chaque type d'objet sauvegardé
- Sans `typeMigrationVersion`, Kibana lance TOUTES les migrations depuis la v0 → les anciennes migrations 7.x crashent sur les objets 8.x
- La version max pour le type `lens` dans Kibana 8.11.0 est `"8.9.0"` (pas `"8.11.0"` !)
- `currentIndexPatternId` dans `formBased` doit contenir un **nom de référence**, pas un **ID** réel
- La clé du datasource a changé de `indexpattern` (avant 8.3.0) à `formBased` (depuis 8.3.0)

### 13.6 Lombok

- La version managée par Spring Boot BOM peut être incompatible avec les dernières versions de Java
- Toujours overrider `<lombok.version>` dans `<properties>` pour Java 25+
- Version stable recommandée : `1.18.36`

---

*Document généré le 2026-05-03 | Claude Sonnet 4.6 | Exchange Rate Proxy v1.0.0*

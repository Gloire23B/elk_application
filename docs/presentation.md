# Exchange Rate Proxy — Dossier de Présentation

> Document de référence pour l'argumentaire et le diaporama de présentation.
> Projet : Proxy centralisé de taux de change — Stack Kafka + Elasticsearch + Kibana

---

## Sommaire

1. [Architecture et structure du projet](#1-architecture-et-structure-du-projet)
2. [Le frontend — rôle des données affichées](#2-le-frontend--rôle-des-données-affichées)
3. [Les graphiques Kibana — analyse et lecture](#3-les-graphiques-kibana--analyse-et-lecture)
4. [Les APIs REST — rôle et localisation dans le code](#4-les-apis-rest--rôle-et-localisation-dans-le-code)
5. [Performance, scalabilité et choix technologiques](#5-performance-scalabilité-et-choix-technologiques)

---

## 1. Architecture et structure du projet

### 1.1 Vue d'ensemble de l'architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     SOURCES DE DONNÉES                          │
│          API Externe : api.exchangerate-api.com/v4              │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS (toutes les 60 min)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SPRING BOOT — PORT 8090                        │
│   ┌───────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐  │
│   │ Scheduler │──▶│ Service  │──▶│  Kafka   │   │ REST API │  │
│   │ (60 min)  │   │  Layer   │   │ Producer │   │ /api/**  │  │
│   └───────────┘   └──────────┘   └────┬─────┘   └──────────┘  │
└────────────────────────────────────────┼────────────────────────┘
                                         │
                             ┌───────────▼──────────┐
                             │   APACHE KAFKA        │
                             │   Port 9092           │
                             │   Topic: exchange-    │
                             │   rates-topic         │
                             └───────────┬───────────┘
                                         │ Consumer Group
                             ┌───────────▼──────────┐
                             │  ELASTICSEARCH 8.11  │
                             │  Port 9200           │
                             │  Index: exchange-    │
                             │  rates-*             │
                             └───────────┬──────────┘
                                         │
                     ┌───────────────────┴──────────────────┐
                     │                                       │
           ┌─────────▼────────┐               ┌─────────────▼──────────┐
           │  KIBANA 8.11.0   │               │  FRONTEND HTML          │
           │  Port 5601       │               │  frontend/index.html    │
           │  Dashboard +     │               │  Dashboard temps réel   │
           │  Visualisations  │               │  Port : fichier local   │
           └──────────────────┘               └────────────────────────┘
```

**Principe fondamental** : l'API externe n'est appelée qu'une seule fois par heure par le scheduler, quel que soit le nombre de requêtes reçues. Toutes les 15 000 équipes consommatrices lisent depuis Elasticsearch ou Kafka — jamais directement depuis l'API externe.

---

### 1.2 Structure des dossiers et rôle de chaque fichier

```
exchange-rate-proxy/
│
├── src/main/java/com/proxy/exchangerate/
│   │
│   ├── ExchangeRateProxyApplication.java        ← Point d'entrée Spring Boot (@SpringBootApplication)
│   │
│   ├── config/                                  ← Configurations techniques (beans Spring)
│   │   ├── ElasticsearchConfig.java             ← Client Elasticsearch + ObjectMapper (JavaTimeModule)
│   │   ├── KafkaConfig.java                     ← Topics Kafka, serialiseurs JSON, partitions
│   │   ├── SecurityConfig.java                  ← HTTP Basic Auth, CORS, règles d'accès
│   │   └── WebClientConfig.java                 ← Client HTTP réactif pour l'API externe
│   │
│   ├── controller/                              ← Couche exposition REST (entrée des requêtes HTTP)
│   │   ├── ExchangeRateController.java          ← 4 endpoints métier /api/exchange-rates/**
│   │   └── RootController.java                  ← Endpoint d'accueil GET / (info API)
│   │
│   ├── service/                                 ← Couche métier (logique applicative)
│   │   ├── ExchangeRateService.java             ← Interface du contrat de service
│   │   └── ExchangeRateServiceImpl.java         ← Orchestration : API → Kafka → Elasticsearch
│   │
│   ├── kafka/                                   ← Couche messagerie asynchrone
│   │   ├── producer/ExchangeRateProducer.java   ← Publie les taux sur le topic Kafka
│   │   └── consumer/ExchangeRateConsumer.java   ← Écoute Kafka et persiste dans Elasticsearch
│   │
│   ├── scheduler/                               ← Tâche planifiée automatique
│   │   └── ExchangeRateScheduler.java           ← Fetch horaire des 7 devises configurées
│   │
│   ├── model/                                   ← Modèles de données
│   │   ├── ExchangeRateDocument.java            ← Document indexé dans Elasticsearch (@Document)
│   │   └── ExchangeRateApiResponse.java         ← Désérialisation de la réponse API externe
│   │
│   ├── repository/                              ← Couche accès aux données
│   │   └── ExchangeRateRepository.java          ← Spring Data ES : findTopByBase, findByDateRange
│   │
│   ├── dto/                                     ← Objets de transfert (format des réponses JSON)
│   │   └── ApiResponse.java                     ← Enveloppe uniforme {success, data, error, timestamp}
│   │
│   └── exception/                               ← Gestion centralisée des erreurs
│       ├── ExchangeRateException.java           ← Exception métier personnalisée
│       └── GlobalExceptionHandler.java          ← Transforme toute exception en JSON standardisé
│
├── src/main/resources/
│   └── application.properties                   ← Configuration : ports, Kafka, ES, scheduler, logs
│
├── src/test/java/com/proxy/exchangerate/        ← Tests unitaires et d'intégration
│   ├── service/ExchangeRateServiceTest.java     ← Tests du service métier
│   ├── controller/ExchangeRateControllerTest.java ← Tests des endpoints REST
│   ├── kafka/producer/ExchangeRateProducerTest.java
│   ├── kafka/consumer/ExchangeRateConsumerTest.java
│   └── repository/ExchangeRateRepositoryTest.java
│
├── frontend/
│   └── index.html                               ← Dashboard HTML/JS temps réel (Tailwind CSS)
│
├── kibana/
│   └── exchange-rate-dashboard.ndjson           ← Export Kibana : index pattern + 3 visuels + 1 dashboard
│
├── elasticsearch/
│   └── index-template.json                      ← Template d'index ES : mappings stricts des champs
│
├── docs/
│   ├── Guide-complet.md                         ← Guide de test A-Z du projet
│   ├── rapport-technique.md                     ← Rapport des choix techniques et bugs résolus
│   └── presentation.md                          ← Ce fichier
│
├── docker-compose.yml                           ← Orchestration 4 services : Kafka, ES, Kibana, App
├── Dockerfile                                   ← Build multi-stage de l'application Spring Boot
├── pom.xml                                      ← Dépendances Maven (Spring Boot 3.2.0, Java 17+)
└── README.md                                    ← Documentation principale du projet
```

---

### 1.3 Les flux de données — comment circule l'information

**Flux 1 — Collecte automatique (Scheduler, toutes les heures)**

```
ExchangeRateScheduler
  └─▶ ExchangeRateService.fetchAndPublish("USD") × 7 devises
        └─▶ WebClient → GET https://api.exchangerate-api.com/v4/latest/USD
              └─▶ ExchangeRateProducer.publish(document)
                    └─▶ Topic Kafka : "exchange-rates-topic"
                          └─▶ ExchangeRateConsumer.consume(document)
                                └─▶ ExchangeRateRepository.save(document)
                                      └─▶ Index Elasticsearch : "exchange-rates-2024-05"
```

**Flux 2 — Requête d'un client REST**

```
Client HTTP
  └─▶ GET /api/exchange-rates/latest/USD  [Authorization: Basic ...]
        └─▶ ExchangeRateController.getLatestRates("USD")
              └─▶ ExchangeRateService.getLatestRates("USD")
                    └─▶ ExchangeRateRepository.findTopByBaseOrderByIndexedAtDesc("USD")
                          └─▶ Elasticsearch → document le plus récent
                                └─▶ ApiResponse{success:true, data:{...taux...}}
```

**Flux 3 — Refresh forcé**

```
Client HTTP
  └─▶ POST /api/exchange-rates/refresh/EUR
        └─▶ ExchangeRateService.fetchAndPublish("EUR")
              └─▶ [même flux que Flux 1, mais déclenché manuellement]
```

---

## 2. Le frontend — rôle des données affichées

Le frontend est une **page HTML autonome** (`frontend/index.html`) qui interroge directement l'API REST Spring Boot. Il ne nécessite aucun serveur web supplémentaire.

### 2.1 Présentation de l'interface

```
┌─────────────────────────────────────────────────────────────────┐
│  ● Exchange Rate Proxy — Temps Réel via Kafka + Elasticsearch  │
│                                              [↻ Refresh]        │
├─────────────────────────────────────────────────────────────────┤
│  DEVISES ACTIVES │  EUR / USD   │  GBP / USD   │  JPY / USD    │
│       152        │   0.9203     │   0.7891     │   149.52      │
├─────────────────────────────────────────────────────────────────┤
│  Devise base : [USD] [EUR] [GBP] [JPY] [CHF] [CAD]             │
├────────────────────────────────────────────────────────────────-┤
│  Taux de change — Base : USD                                    │
│  DEVISE │    TAUX    │  INVERSE  │    NOM           │ VARIATION │
│  EUR    │  0.920300  │ 1.086612  │ Euro             │    ▲      │
│  GBP    │  0.789100  │ 1.267267  │ Livre Sterling   │    —      │
│  JPY    │ 149.520000 │ 0.006687  │ Yen Japonais     │    ▼      │
│  ...    │    ...     │    ...    │    ...           │    ...    │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Explication de chaque donnée affichée

| Donnée | Source | Explication |
|--------|--------|-------------|
| **Indicateur vert clignotant** | Statique | Signal visuel de connexion active (style "live") |
| **Dernière MAJ** | Horloge locale | Horodatage du dernier appel API réussi |
| **DEVISES ACTIVES** | `doc.rates` — nombre de clés | Combien de devises sont disponibles dans le document le plus récent |
| **EUR / USD** | `doc.rates.EUR` | Taux EUR pour la base sélectionnée (souvent USD) |
| **GBP / USD** | `doc.rates.GBP` | Taux GBP pour la base sélectionnée |
| **JPY / USD** | `doc.rates.JPY` | Taux JPY pour la base sélectionnée |
| **Boutons [USD] [EUR] ...** | Configuration JS | Sélecteur de devise de base — déclenche un nouvel appel API |
| **Colonne DEVISE** | Clé de `doc.rates` | Code ISO 4217 de la devise cible (3 lettres) |
| **Colonne TAUX** | Valeur de `doc.rates[currency]` | Combien d'unités de la devise cible pour 1 unité de base |
| **Colonne INVERSE** | `1 / taux` (calculé côté JS) | Combien d'unités de base pour 1 unité de la devise cible |
| **Colonne NOM** | Dictionnaire JS (`CURRENCY_NAMES`) | Traduction française du code devise |
| **Colonne VARIATION** | Comparaison avec `previousRates` en mémoire | ▲ hausse / ▼ baisse / — stable depuis le dernier refresh |

### 2.3 Comportements dynamiques

- **Auto-refresh toutes les 60 secondes** : `setInterval(() => loadRates(currentBase), 60000)`
- **Changement de devise de base** : clic sur un bouton → appel `GET /api/exchange-rates/latest/{base}`, effacement de `previousRates` pour réinitialiser les variations
- **Mode dégradé (API indisponible)** : affichage de données de démonstration statiques avec bandeau d'avertissement jaune — l'utilisateur voit toujours quelque chose même si le backend est down
- **Animations** : `fade-in` sur chaque ligne à chaque refresh, flèches de variation colorées (vert ▲, rouge ▼)

### 2.4 Authentification côté frontend

```javascript
const AUTH_HDR = 'Basic ' + btoa('api-user:proxy-secret-2026');
// Envoyé dans l'en-tête Authorization de chaque requête fetch()
```

La page encode les credentials en Base64 (HTTP Basic Auth) et les inclut dans chaque requête. Ce mécanisme est suffisant pour un réseau interne ; pour une exposition publique, il faudrait utiliser OAuth2/JWT.

---

## 3. Les graphiques Kibana — analyse et lecture

Le tableau de bord Kibana (`kibana/exchange-rate-dashboard.ndjson`) contient **3 visualisations Lens** importées en même temps qu'un index pattern et un dashboard.

### 3.1 Graphique 1 — Évolution EUR/USD dans le temps

**Type** : Lens `lnsXY` (courbe temporelle)  
**Titre** : "Évolution EUR/USD dans le temps"  
**Emplacement** : Panneau supérieur du dashboard (largeur totale)

```
Taux EUR
  1.09 ┤                          ╭─╮
  1.08 ┤               ╭──╮      ╯  ╰──
  1.07 ┤     ╭────╮   ╯   ╰──╮
  1.06 ┤    ╯     ╰──╯        ╰────╮
  1.05 ┤───╯                        ╰──
       └──────────────────────────────▶ temps (auto-interval)
       il y a 7 jours              maintenant
```

**Ce que ce graphique permet de lire** :
- L'**évolution du taux de change EUR pour 1 USD** heure par heure sur les 7 derniers jours
- Les **tendances haussières ou baissières** de l'euro face au dollar
- Les **anomalies** : pics soudains ou creux inhabituels (indicateurs d'événements économiques)
- La **fréquence de rafraîchissement** : chaque point = 1 fetch horaire du scheduler

**Configuration technique** :
- Axe X : `date_histogram` sur le champ `indexedAt` (date d'indexation dans Elasticsearch)
- Axe Y : `average` du champ `rates.EUR` (moyenne sur l'intervalle de temps)
- Filtre : `base = USD` — seuls les documents ayant USD comme devise de base
- Plage temporelle : 7 derniers jours (`now-7d`)
- Intervalle : automatique (Kibana ajuste selon le zoom)

---

### 3.2 Graphique 2 — Derniers taux de change (tableau)

**Type** : Lens `lnsDatatable` (tableau de données)  
**Titre** : "Derniers taux de change"  
**Emplacement** : Panneau bas-gauche du dashboard

```
┌──────────────────┬─────────────────────┐
│ Devise de base   │ Nombre de documents │
├──────────────────┼─────────────────────┤
│ AUD              │         168         │
│ CAD              │         168         │
│ CHF              │         168         │
│ EUR              │         168         │
│ GBP              │         168         │
│ JPY              │         168         │
│ USD              │         168         │
└──────────────────┴─────────────────────┘
```

**Ce que ce tableau permet de lire** :
- **Quelles devises de base sont surveillées** par le scheduler (les 7 : USD, EUR, GBP, JPY, CHF, CAD, AUD)
- **La volumétrie des données** : combien de snapshots horaires ont été enregistrés pour chaque devise
- **L'équilibre de la collecte** : si une devise a nettement moins de documents que les autres, cela révèle des échecs de fetch ou un démarrage tardif

**Configuration technique** :
- Dimension Lignes : `terms` sur le champ `base` (devise de base, keyword)
- Dimension Métriques : `count` — nombre de documents indexés
- Tri : par nombre de documents décroissant
- Pas de filtre temporel propre : hérite du filtre global du dashboard (7 jours)

---

### 3.3 Graphique 3 — Distribution des devises de base (camembert)

**Type** : Lens `lnsPie` (graphique en secteurs)  
**Titre** : "Distribution des devises de base"  
**Emplacement** : Panneau bas-droit du dashboard

```
          USD 14,3%
        ╱──────────╲
  AUD 14,3%        EUR 14,3%
  │                    │
  ╰──────────────────────╯
  CAD 14,3%        GBP 14,3%
        ╲──────────╱
          JPY 14,3%  CHF 14,3%
```

**Ce que ce graphique permet de lire** :
- La **répartition équilibrée** (ou non) des collectes entre devises — idéalement toutes à ~14,3% (1/7)
- Identifier visuellement si **une devise monopolise** les données (déséquilibre de collecte)
- La **proportion relative** de chaque devise dans l'historique total

**Configuration technique** :
- Breakdown : `terms` sur le champ `base` (même que le tableau)
- Métriques : `count` de documents
- Affichage : pourcentages sur les secteurs
- Topologie : donut ou pie complet selon le thème Kibana

---

### 3.4 Paramètres globaux du dashboard

| Paramètre | Valeur | Explication |
|-----------|--------|-------------|
| Plage temporelle | `now-7d` à `now` | Fenêtre glissante des 7 derniers jours |
| Auto-refresh | 60 secondes | Le dashboard se met à jour automatiquement |
| Index pattern | `exchange-rates*` | Couvre tous les indices ES de la collection |
| Champ temporel | `indexedAt` | Timestamp d'indexation Elasticsearch |

---

## 4. Les APIs REST — rôle et localisation dans le code

### 4.1 Vue d'ensemble des endpoints

| Méthode | URL | Auth | Rôle |
|---------|-----|------|------|
| `GET` | `/` | Non | Info API et liste des endpoints |
| `GET` | `/api/exchange-rates/latest/{base}` | Oui | Derniers taux pour une devise |
| `GET` | `/api/exchange-rates/rate?from=&to=` | Oui | Taux entre 2 devises |
| `GET` | `/api/exchange-rates/history/{base}?fromDate=&toDate=` | Oui | Historique sur une période |
| `POST` | `/api/exchange-rates/refresh/{base}` | Oui | Refresh forcé depuis l'API externe |
| `GET` | `/actuator/health` | Non | Santé applicative |
| `GET` | `/actuator/metrics` | Oui | Métriques JVM et applicatives |

**Authentification** : HTTP Basic Auth — `api-user` / `proxy-secret-2026`

---

### 4.2 API 1 — `GET /` — Page d'accueil

**Fichier** : [RootController.java](../src/main/java/com/proxy/exchangerate/controller/RootController.java)

**Rôle** : Endpoint public retournant une carte JSON décrivant l'API (nom, version, liste des endpoints disponibles). Permet à un développeur de découvrir l'API sans documentation externe.

**Réponse** :
```json
{
  "application": "Exchange Rate Proxy",
  "version": "1.0.0",
  "description": "Proxy centralisé de taux de change — Kafka + Elasticsearch",
  "authentication": "HTTP Basic — Utiliser les credentials fournis pour /api/**",
  "endpoints": {
    "GET  /api/exchange-rates/latest/{base}": "Derniers taux pour une devise",
    "GET  /api/exchange-rates/rate?from=USD&to=EUR": "Taux de conversion entre deux devises",
    ...
  }
}
```

---

### 4.3 API 2 — `GET /api/exchange-rates/latest/{base}` — Derniers taux

**Fichier** : [ExchangeRateController.java:48](../src/main/java/com/proxy/exchangerate/controller/ExchangeRateController.java#L48)  
**Service** : [ExchangeRateServiceImpl.java:67](../src/main/java/com/proxy/exchangerate/service/ExchangeRateServiceImpl.java#L67)

**Rôle** : Retourner le snapshot le plus récent des taux de change pour une devise de base donnée. Si aucun document n'est en cache dans Elasticsearch, déclenche automatiquement un fetch depuis l'API externe.

**Validation** : `{base}` doit être un code ISO 4217 valide — 3 lettres majuscules (`^[A-Z]{3}$`)

**Logique** :
```
1. repository.findTopByBaseOrderByIndexedAtDesc(base)
   ├── Trouvé → retourner le document (lecture ES rapide)
   └── Non trouvé → fetchAndPublish(base) → appel API externe + Kafka + ES
```

**Réponse** :
```json
{
  "success": true,
  "data": {
    "id": "USD_2026-05-03_a1b2c3d4",
    "base": "USD",
    "timestamp": "2026-05-03",
    "indexedAt": "2026-05-03T14:00:00",
    "source": "exchangerate-api.com",
    "rates": { "EUR": 0.9203, "GBP": 0.7891, "JPY": 149.52, ... }
  },
  "timestamp": "2026-05-03T14:00:01Z"
}
```

---

### 4.4 API 3 — `GET /api/exchange-rates/rate?from=USD&to=EUR` — Taux unitaire

**Fichier** : [ExchangeRateController.java:73](../src/main/java/com/proxy/exchangerate/controller/ExchangeRateController.java#L73)  
**Service** : [ExchangeRateServiceImpl.java:80](../src/main/java/com/proxy/exchangerate/service/ExchangeRateServiceImpl.java#L80)

**Rôle** : Retourner un seul taux de conversion entre deux devises. Endpoint le plus léger — utile pour les microservices qui n'ont besoin que d'un seul chiffre.

**Logique** :
```
getLatestRates(from) → doc.getRateFor(to) → Double
```

**Réponse** :
```json
{
  "success": true,
  "data": 0.9203,
  "timestamp": "2026-05-03T14:00:01Z"
}
```

---

### 4.5 API 4 — `GET /api/exchange-rates/history/{base}` — Historique

**Fichier** : [ExchangeRateController.java:97](../src/main/java/com/proxy/exchangerate/controller/ExchangeRateController.java#L97)  
**Repository** : [ExchangeRateRepository.java](../src/main/java/com/proxy/exchangerate/repository/ExchangeRateRepository.java)

**Rôle** : Récupérer tous les snapshots d'une devise sur une plage de dates. Permet l'analyse de tendances, les backtests financiers et l'alimentation de graphiques d'évolution.

**Paramètres** :
- `base` : devise de base (ex: `USD`)
- `fromDate` : date de début au format `yyyy-MM-dd`
- `toDate` : date de fin au format `yyyy-MM-dd`

**Réponse** :
```json
{
  "success": true,
  "data": [
    { "base": "USD", "timestamp": "2026-04-27", "rates": {...} },
    { "base": "USD", "timestamp": "2026-04-28", "rates": {...} },
    ...
  ],
  "timestamp": "2026-05-03T14:00:01Z"
}
```

---

### 4.6 API 5 — `POST /api/exchange-rates/refresh/{base}` — Refresh forcé

**Fichier** : [ExchangeRateController.java:116](../src/main/java/com/proxy/exchangerate/controller/ExchangeRateController.java#L116)  
**Service** : [ExchangeRateServiceImpl.java:49](../src/main/java/com/proxy/exchangerate/service/ExchangeRateServiceImpl.java#L49)

**Rôle** : Déclencher manuellement un fetch depuis l'API externe, sans attendre le prochain cycle horaire. Utile pour forcer une mise à jour après un événement économique majeur (annonce de la Fed, crise de change, etc.).

**Séquence complète déclenchée** :
```
POST /refresh/USD
  → WebClient.get("https://api.exchangerate-api.com/v4/latest/USD")
  → ExchangeRateProducer.publish(document)  [→ Kafka topic]
  → ExchangeRateConsumer.consume()          [← Kafka consumer]
  → ExchangeRateRepository.save(document)  [→ Elasticsearch]
  → Retour du nouveau document au client
```

---

### 4.7 Enveloppe de réponse unifiée — `ApiResponse<T>`

**Fichier** : [ApiResponse.java](../src/main/java/com/proxy/exchangerate/dto/ApiResponse.java)

Tous les endpoints retournent la même structure JSON :

```json
{
  "success": true | false,
  "data":    { ... } | null,
  "error":   null | "Message d'erreur",
  "timestamp": "2026-05-03T14:00:01Z"
}
```

**Avantage** : le client n'a jamais à gérer des formats de réponse différents selon l'endpoint — il vérifie toujours `success` puis lit `data` ou `error`.

---

### 4.8 Codes HTTP retournés

| Situation | Code HTTP | Raison |
|-----------|-----------|--------|
| Succès | `200 OK` | Données retournées normalement |
| Devise invalide | `400 Bad Request` | Violation de contrainte `@Pattern` |
| Route inconnue | `404 Not Found` | `NoHandlerFoundException` |
| API externe down | `502 Bad Gateway` | `WebClientResponseException` |
| Erreur interne | `503 Service Unavailable` | `ExchangeRateException` métier |
| Inattendu | `500 Internal Server Error` | Catch-all `Exception` |

---

## 5. Performance, scalabilité et choix technologiques

### 5.1 Architecture découplée — le cœur de la scalabilité

**Problème résolu** : 15 000 équipes internes qui ont besoin des taux de change. Si chacune interrogeait directement l'API externe, cela représenterait 15 000 × 7 devises = **105 000 appels API par heure** — potentiellement bloqué par les rate limits ou facturé à la requête.

**Solution** : le proxy réduit cela à **7 appels par heure** (1 par devise), peu importe la charge entrante.

```
Sans proxy :
  15 000 équipes × 7 devises × 24h = 2 520 000 appels/jour vers l'API externe

Avec le proxy :
  1 scheduler × 7 devises × 24h = 168 appels/jour vers l'API externe
  Réduction : 99,993%
```

---

### 5.2 Apache Kafka — messagerie événementielle

**Pourquoi Kafka plutôt qu'un accès direct à ES ?**

| Critère | Sans Kafka | Avec Kafka |
|---------|-----------|------------|
| Découplage | Service → ES couplés | Producer ≠ Consumer |
| Résilience | Si ES est down, données perdues | Messages conservés dans Kafka |
| Rejeu | Impossible | Replay depuis le début du topic |
| Consommateurs | 1 seul lecteur | N consommateurs en parallèle |
| Audit | Aucun | Log immuable de tous les événements |

**Configuration** :
- **KRaft mode** (sans ZooKeeper) : Kafka 3.7.1 est autonome
- **3 partitions** : parallélisation possible des consumers
- **acks=all** : le producer attend la confirmation de tous les replicas
- **retries=3** : 3 tentatives en cas d'échec réseau

---

### 5.3 Elasticsearch — stockage et recherche haute performance

**Pourquoi Elasticsearch plutôt qu'une base relationnelle ?**

| Critère | PostgreSQL / MySQL | Elasticsearch |
|---------|-------------------|---------------|
| Recherche full-text | Lent (LIKE, full-scan) | Natif, indexé inversé |
| Scalabilité horizontale | Difficile (sharding complexe) | Natif (shards + replicas) |
| Agrégations temporelles | Lent sur gros volumes | Optimisé (date_histogram) |
| Schéma | Rigide (ALTER TABLE) | Flexible (dynamic mapping) |
| Kibana | Non | Intégration native |

**Configuration** :
- `refresh_interval: 5s` — les nouveaux documents sont visibles en 5 secondes
- `number_of_shards: 1` — single-node dev, extensible en prod
- `number_of_replicas: 0` — dev (1+ en prod pour la tolérance aux pannes)
- **Index dynamique** : l'index est nommé `exchange-rates-YYYY-MM` — rotation mensuelle automatique

---

### 5.4 Spring Boot 3.2.0 — socle applicatif

**Pourquoi Spring Boot ?**

- **Auto-configuration** : Kafka, Elasticsearch, Security, Actuator configurés avec un minimum de code
- **Spring Data Elasticsearch** : requêtes Elasticsearch sans SQL ni DSL JSON brut — méthodes comme `findTopByBaseOrderByIndexedAtDesc`
- **Spring WebFlux / WebClient** : appels HTTP non-bloquants vers l'API externe (réactif)
- **Spring Security** : HTTP Basic Auth + CORS en quelques lignes de configuration
- **Spring Actuator** : endpoints `/health`, `/metrics` offerts gratuitement pour le monitoring

---

### 5.5 Docker Compose — infrastructure reproductible

**Avantage principal** : l'ensemble de l'infrastructure (Kafka, Elasticsearch, Kibana, App) se lance avec **une seule commande** :

```bash
docker-compose up -d
```

**Configuration des health checks** : l'application Spring Boot ne démarre qu'après la validation de la santé de Kafka ET d'Elasticsearch — plus d'erreurs de démarrage dues à une race condition.

```yaml
depends_on:
  kafka:
    condition: service_healthy
  elasticsearch:
    condition: service_healthy
```

---

### 5.6 Stack de sécurité

| Couche | Mécanisme | Fichier |
|--------|-----------|---------|
| Authentification | HTTP Basic Auth (BCrypt) | SecurityConfig.java |
| Autorisation | `ROLE_API`, `ROLE_ADMIN` | SecurityConfig.java |
| CORS | Origines configurables | SecurityConfig.java |
| Validation | `@Pattern`, `@NotBlank`, `@Size` | ExchangeRateController.java |
| Session | Stateless (JWT-ready) | SecurityConfig.java |
| Erreurs | JSON uniforme, sans stack trace | GlobalExceptionHandler.java |

**Utilisateurs configurés** :
- `api-user` / `proxy-secret-2026` → `ROLE_API` (lecture seule + refresh)
- `admin` / `admin-secret-2026` → `ROLE_ADMIN` + `ROLE_API` (accès total)

---

### 5.7 Résilience et gestion des erreurs

**Pattern de résilience** :

```
Appel API externe
  ├── Succès → Publication Kafka → Indexation ES
  ├── HTTP 4xx/5xx → WebClientResponseException → 502 Bad Gateway (loggé)
  ├── Réponse null → ExchangeRateException → 503 Service Unavailable
  └── Erreur réseau → ExchangeRateException → 503 + log ERROR

Frontend (index.html)
  ├── API disponible → Affichage des données réelles
  └── API indisponible → Données de démonstration + bandeau d'avertissement
```

La dépendance `resilience4j-spring-boot3` est disponible pour ajouter des **circuit breakers** en production (disjoncteur automatique après N échecs consécutifs).

---

### 5.8 Tableau de synthèse des technologies

| Technologie | Version | Rôle | Pourquoi ce choix |
|-------------|---------|------|-------------------|
| **Spring Boot** | 3.2.0 | Socle applicatif Java | Standard industriel, ecosystème riche |
| **Apache Kafka** | 3.7.1 | Bus de messages événementiel | Haut débit, rejouable, découplage |
| **Elasticsearch** | 8.11.0 | Stockage et recherche | Agrégations temporelles, Kibana natif |
| **Kibana** | 8.11.0 | Visualisation analytique | Intégration ES native, Lens moderne |
| **Docker Compose** | 3.8 | Orchestration infrastructure | Reproductibilité, isolation, CI/CD |
| **Spring Security** | 6.x | Authentification/autorisation | Intégration Spring Boot, BCrypt |
| **WebClient** | Spring 6 | Client HTTP réactif | Non-bloquant, timeouts configurables |
| **Spring Data ES** | 5.x | Couche repository ES | Abstraction requêtes, pagination |
| **Lombok** | 1.18.36 | Réduction boilerplate | Compatibilité Java 17+/25 |
| **Resilience4j** | 2.1.0 | Circuit breaker | Tolérance aux pannes en prod |
| **Tailwind CSS** | CDN | UI frontend | Rapidité, pas de build step |

---

### 5.9 Points forts du projet pour la présentation

1. **Architecture event-driven** : les composants ne se connaissent pas directement — le Producer ne sait pas qu'un Consumer existe, et le Consumer ne sait pas d'où viennent les messages.

2. **Zero downtime sur l'API externe** : si `api.exchangerate-api.com` est indisponible pendant 2 heures, les 15 000 équipes continuent à lire les données précédentes depuis Elasticsearch — aucune erreur visible.

3. **Observabilité complète** : logs structurés (pattern console/file), Actuator health, métriques JVM, dashboard Kibana — la stack est monitorable à tous les niveaux.

4. **Infrastructure as Code** : l'intégralité de l'infrastructure est décrite dans `docker-compose.yml` — reproductible sur n'importe quelle machine avec Docker installé.

5. **9 bugs anticipés et résolus** : le projet documente les pièges courants de l'intégration Kafka/ES/Kibana avec les solutions appliquées — valeur pédagogique élevée.

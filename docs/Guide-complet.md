# Guide de test complet — Exchange Rate Proxy

> Ce guide couvre l'intégralité du projet de A à Z : infrastructure, API, frontend, Kibana, tests unitaires, et vérification du flux Kafka → Elasticsearch.

---

## Prérequis

| Outil | Version minimale | Vérification |
|-------|-----------------|--------------|
| Docker Desktop | 24.x | `docker --version` |
| Java | 17+ (testé Java 25) | `java --version` |
| Maven | 3.6+ | `mvn --version` |
| curl | tout | `curl --version` |

> **Maven manquant sur Windows ?**
> ```powershell
> scoop install maven
> $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
> ```

---

## Étape 1 — Démarrer l'infrastructure Docker

```bash
docker-compose up -d kafka elasticsearch kibana
```

Attendre que les trois conteneurs soient healthy (environ 30–60 secondes) :

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

Résultat attendu :

```
NAMES                    STATUS
proxy-kafka              Up X seconds (healthy)
proxy-elasticsearch      Up X seconds (healthy)
proxy-kibana             Up X seconds
```

### Vérifier Elasticsearch

```bash
curl -s http://localhost:9200/_cluster/health | python -m json.tool
```

Champ `status` attendu : `green` ou `yellow` (single-node = yellow normal).

### Vérifier Kibana

```bash
curl -s http://localhost:5601/api/status | python -c "import sys,json; d=json.load(sys.stdin); print('Kibana', d['version']['number'], '—', d['status']['overall']['level'])"
```

Résultat attendu : `Kibana 8.11.0 — available`

---

## Étape 2 — Importer le dashboard Kibana

Avant de démarrer l'application, importer le dashboard Kibana (le fichier NDJSON contient l'index pattern, les 3 visualisations et le dashboard) :

```bash
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@kibana/exchange-rate-dashboard.ndjson
```

Résultat attendu :

```json
{"successCount":5,"success":true,"warnings":[],"successResults":[
  {"type":"index-pattern","id":"exchange-rates-index-pattern",...},
  {"type":"lens","id":"viz-rates-over-time-lens",...},
  {"type":"lens","id":"viz-latest-rates-table-lens",...},
  {"type":"lens","id":"viz-base-distribution-lens",...},
  {"type":"dashboard","id":"exchange-rate-proxy-dashboard",...}
]}
```

---

## Étape 3 — Compiler et démarrer l'application

```bash
mvn spring-boot:run
```

Attendre les logs de démarrage (environ 10–15 secondes) :

```
INFO  [ExchangeRateProxyApplication] - Started ExchangeRateProxyApplication in X.XXX seconds
```

> En cas d'erreurs Lombok (cannot find symbol `getBase()`, `builder()`...) vérifier que `pom.xml` contient bien `<lombok.version>1.18.36</lombok.version>`.

### Vérifier l'état applicatif

```bash
curl -s http://localhost:8090/actuator/health | python -m json.tool
```

Résultat attendu :

```json
{
  "status": "UP",
  "components": {
    "elasticsearch": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

---

## Étape 4 — Alimenter Elasticsearch (premier fetch)

Le scheduler tourne toutes les heures. Pour ne pas attendre, forcer le premier chargement pour toutes les devises :

```bash
for CURRENCY in USD EUR GBP JPY CHF CAD AUD; do
  curl -s -u api-user:proxy-secret-2026 \
    -X POST "http://localhost:8090/api/exchange-rates/refresh/$CURRENCY" \
    | python -c "import sys,json; d=json.load(sys.stdin); print('$CURRENCY →', 'OK' if d.get('success') else d)"
done
```

Sur Windows PowerShell :

```powershell
foreach ($currency in @("USD","EUR","GBP","JPY","CHF","CAD","AUD")) {
  $r = Invoke-RestMethod -Uri "http://localhost:8090/api/exchange-rates/refresh/$currency" `
    -Method POST -Headers @{Authorization="Basic YXBpLXVzZXI6cHJveHktc2VjcmV0LTIwMjY="}
  Write-Host "$currency → $($r.success)"
}
```

Vérifier que les documents sont dans Elasticsearch :

```bash
curl -s "http://localhost:9200/exchange-rates/_count"
# → {"count":7,...}  (un document par devise)
```

---

## Étape 5 — Tester l'API REST

### 5.1 Authentification — accès refusé sans credentials

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8090/api/exchange-rates/latest/USD
```

Résultat attendu : `401`

### 5.2 GET /api/exchange-rates/latest/{base} — Derniers taux

```bash
curl -s -u api-user:proxy-secret-2026 \
  http://localhost:8090/api/exchange-rates/latest/USD | python -m json.tool
```

Résultat attendu :

```json
{
  "success": true,
  "data": {
    "id": "USD_2026-05-03_...",
    "base": "USD",
    "timestamp": "2026-05-03",
    "rates": {
      "EUR": 0.89,
      "GBP": 0.79,
      "JPY": 153.5,
      ...
    },
    "indexedAt": "2026-05-03T14:00:00"
  }
}
```

Tester d'autres devises :

```bash
curl -s -u api-user:proxy-secret-2026 http://localhost:8090/api/exchange-rates/latest/EUR | python -m json.tool
curl -s -u api-user:proxy-secret-2026 http://localhost:8090/api/exchange-rates/latest/GBP | python -m json.tool
```

### 5.3 Validation — code devise invalide

```bash
curl -s -u api-user:proxy-secret-2026 http://localhost:8090/api/exchange-rates/latest/usd
# → 400 Bad Request (regex ^[A-Z]{3}$)

curl -s -u api-user:proxy-secret-2026 http://localhost:8090/api/exchange-rates/latest/DOLLAR
# → 400 Bad Request
```

### 5.4 GET /api/exchange-rates/rate — Taux entre deux devises

```bash
curl -s -u api-user:proxy-secret-2026 \
  "http://localhost:8090/api/exchange-rates/rate?from=USD&to=EUR" | python -m json.tool
```

Résultat attendu :

```json
{
  "success": true,
  "data": 0.89
}
```

Autres conversions utiles :

```bash
curl -s -u api-user:proxy-secret-2026 "http://localhost:8090/api/exchange-rates/rate?from=EUR&to=JPY"
curl -s -u api-user:proxy-secret-2026 "http://localhost:8090/api/exchange-rates/rate?from=GBP&to=CHF"
```

### 5.5 GET /api/exchange-rates/history/{base} — Historique

```bash
curl -s -u api-user:proxy-secret-2026 \
  "http://localhost:8090/api/exchange-rates/history/USD?fromDate=2026-04-26&toDate=2026-05-03" \
  | python -m json.tool
```

Résultat attendu : liste de documents avec une entrée par heure sur la période.

### 5.6 POST /api/exchange-rates/refresh/{base} — Forcer un refresh

```bash
curl -s -u api-user:proxy-secret-2026 \
  -X POST http://localhost:8090/api/exchange-rates/refresh/USD | python -m json.tool
```

Ce call :
1. Appelle l'API externe `exchangerate-api.com`
2. Publie le document sur Kafka (topic `exchange-rates`)
3. Le consumer Kafka le reçoit et l'indexe dans Elasticsearch
4. Retourne le document fraîchement récupéré

### 5.7 GET /actuator — Monitoring

```bash
# Santé globale
curl -s http://localhost:8090/actuator/health | python -m json.tool

# Métriques JVM
curl -s http://localhost:8090/actuator/metrics | python -m json.tool

# Info application
curl -s http://localhost:8090/actuator/info
```

---

## Étape 6 — Vérifier le flux Kafka

### 6.1 Vérifier que le topic existe

```bash
docker exec proxy-kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Résultat attendu : `exchange-rates`

### 6.2 Inspecter les messages dans le topic

```bash
docker exec proxy-kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic exchange-rates \
  --from-beginning \
  --max-messages 3
```

Chaque message est un JSON contenant `base`, `timestamp`, `rates`, `indexedAt`.

### 6.3 Vérifier les partitions et les offsets

```bash
docker exec proxy-kafka \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group exchange-rate-consumers \
  --describe
```

La colonne `LAG` doit être à `0` — tous les messages ont été consommés par le consumer Spring.

### 6.4 Vérifier dans les logs Spring que le consumer reçoit bien

```bash
# Chercher les lignes du consumer dans la console de l'application
# (lancer dans un autre terminal pendant que l'app tourne)
curl -s -u api-user:proxy-secret-2026 -X POST http://localhost:8090/api/exchange-rates/refresh/USD
# → Doit afficher dans les logs :
# INFO  [ExchangeRateProducer]  - Message publié sur Kafka : USD
# INFO  [ExchangeRateConsumer]  - Message reçu : USD — indexation ES
```

---

## Étape 7 — Vérifier Elasticsearch

### 7.1 Lister les indices

```bash
curl -s "http://localhost:9200/_cat/indices?v"
```

L'index `exchange-rates` doit apparaître avec un statut `yellow` (normal en single-node).

### 7.2 Vérifier le mapping des champs

```bash
curl -s "http://localhost:9200/exchange-rates/_mapping" | python -m json.tool
```

Champs clés attendus :

| Champ | Type ES |
|-------|---------|
| `base` | `keyword` |
| `indexedAt` | `date` |
| `timestamp` | `keyword` |
| `rates` | `object` (sous-champs dynamiques : `rates.EUR`, `rates.USD`...) |

### 7.3 Compter les documents par devise

```bash
curl -s "http://localhost:9200/exchange-rates/_search?size=0" \
  -H "Content-Type: application/json" \
  -d '{"aggs":{"par_devise":{"terms":{"field":"base","size":10}}}}' \
  | python -m json.tool
```

### 7.4 Requête full-text — dernier document USD

```bash
curl -s "http://localhost:9200/exchange-rates/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {"term": {"base": "USD"}},
    "sort": [{"indexedAt": {"order": "desc"}}],
    "size": 1
  }' | python -m json.tool
```

### 7.5 Requête sur un sous-champ de rates

```bash
curl -s "http://localhost:9200/exchange-rates/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {"term": {"base": "USD"}},
    "_source": ["base", "indexedAt", "rates.EUR", "rates.GBP"],
    "size": 3
  }' | python -m json.tool
```

---

## Étape 8 — Tester le frontend Tailwind

Ouvrir le fichier directement dans un navigateur :

```
file:///d:/exchange-rate-proxy/frontend/index.html
```

Ou via un serveur local (recommandé pour éviter les restrictions CORS du navigateur) :

```bash
# Python
python -m http.server 5500 --directory frontend/
# → http://localhost:5500/frontend/
```

### Points à vérifier

| Élément | Comportement attendu |
|---------|---------------------|
| Point vert clignotant | L'API est joignable |
| Stat "Devises actives" | Nombre de devises dans la réponse (ex: 166) |
| Stat "EUR/USD" | Taux EUR par rapport à USD (ex: 0.8912) |
| Stat "GBP/USD" | Taux GBP |
| Stat "JPY/USD" | Taux JPY |
| Boutons USD/EUR/GBP/JPY/CHF/CAD | Changement de devise de base → tableau se recharge |
| Tableau des taux | Une ligne par devise avec taux et taux inverse |
| Indicateurs ▲▼ | Variation par rapport au fetch précédent (après 60s) |
| Bouton ↻ Refresh | Recharge immédiate depuis l'API |

**Si "API indisponible — Affichage des données de démonstration" s'affiche :**
- Vérifier que Spring Boot tourne sur le port `8090`
- Vérifier les CORS si le frontend est servi depuis une origine différente

---

## Étape 9 — Tester le dashboard Kibana

Ouvrir dans un navigateur :

```
http://localhost:5601
```

Navigation : **Analytics → Dashboards → Exchange Rate Proxy — Tableau de bord**

### Visualisations attendues

| Panneau | Type | Données affichées |
|---------|------|-------------------|
| Évolution EUR/USD dans le temps | Courbe (lnsXY) | Moyenne de `rates.EUR` pour les documents `base=USD`, regroupés par heure |
| Derniers taux de change | Tableau (lnsDatatable) | Nombre de documents indexés par devise de base |
| Distribution des devises de base | Camembert (lnsPie) | Répartition en % des devises dans l'index |

### Points à vérifier

- La fenêtre temporelle est "Last 7 days" par défaut
- Le rafraîchissement automatique est activé (1 minute)
- La courbe EUR/USD montre des points pour chaque heure où le scheduler a tourné
- Le tableau liste les 7 devises (USD, EUR, GBP, JPY, CHF, CAD, AUD)
- Le camembert montre ~14% par devise (répartition équilibrée)

### Réimporter le dashboard si besoin

```bash
# Supprimer les objets existants
curl -X DELETE "http://localhost:5601/api/saved_objects/dashboard/exchange-rate-proxy-dashboard" -H "kbn-xsrf: true"
curl -X DELETE "http://localhost:5601/api/saved_objects/lens/viz-rates-over-time-lens" -H "kbn-xsrf: true"
curl -X DELETE "http://localhost:5601/api/saved_objects/lens/viz-latest-rates-table-lens" -H "kbn-xsrf: true"
curl -X DELETE "http://localhost:5601/api/saved_objects/lens/viz-base-distribution-lens" -H "kbn-xsrf: true"
curl -X DELETE "http://localhost:5601/api/saved_objects/index-pattern/exchange-rates-index-pattern" -H "kbn-xsrf: true"

# Réimporter
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@kibana/exchange-rate-dashboard.ndjson
```

---

## Étape 10 — Lancer les tests unitaires

```bash
mvn test
```

Résultat attendu :

```
[INFO] Results:
[INFO]
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

### Détail des suites de tests

| Suite | Couverture |
|-------|-----------|
| `ExchangeRateServiceTest` (6 tests) | `getLatestRates`, `fetchAndPublish`, `getRate`, `getRatesHistory` — mock Kafka + ES |
| `ExchangeRateControllerTest` (6 tests) | Tous les endpoints REST avec `MockMvc` + `@WithMockUser` |
| `ExchangeRateProducerTest` (5 tests) | Serialisation JSON, clé Kafka = devise, idempotence |
| `ExchangeRateConsumerTest` (4 tests) | Réception message Kafka → indexation ES |
| `ExchangeRateRepositoryTest` (5 tests) | Requêtes Spring Data Elasticsearch (mocked) |

### Lancer une suite spécifique

```bash
mvn test -Dtest=ExchangeRateControllerTest
mvn test -Dtest=ExchangeRateServiceTest
```

---

## Étape 11 — Vérification du flux bout en bout

Ce test vérifie la chaîne complète : API externe → Kafka → Elasticsearch → API REST.

```bash
# 1. Compter les documents USD avant
AVANT=$(curl -s "http://localhost:9200/exchange-rates/_count?q=base:USD" | python -c "import sys,json; print(json.load(sys.stdin)['count'])")
echo "Documents USD avant : $AVANT"

# 2. Forcer un refresh
curl -s -u api-user:proxy-secret-2026 -X POST http://localhost:8090/api/exchange-rates/refresh/USD > /dev/null

# 3. Attendre 2 secondes (consumer Kafka asynchrone)
sleep 2

# 4. Compter les documents USD après
APRES=$(curl -s "http://localhost:9200/exchange-rates/_count?q=base:USD" | python -c "import sys,json; print(json.load(sys.stdin)['count'])")
echo "Documents USD après  : $APRES"

# 5. Vérifier l'incrémentation
if [ "$APRES" -gt "$AVANT" ]; then
  echo "✅ Flux bout en bout OK — +$((APRES - AVANT)) document(s)"
else
  echo "⚠️  Aucun nouveau document — vérifier les logs Kafka/Consumer"
fi
```

---

## Résumé des URLs et credentials

| Service | URL | Accès |
|---------|-----|-------|
| API Spring Boot | `http://localhost:8090` | `api-user` / `proxy-secret-2026` |
| Actuator | `http://localhost:8090/actuator/health` | public |
| Elasticsearch | `http://localhost:9200` | public (dev) |
| Kibana | `http://localhost:5601` | public (dev) |
| Frontend | `frontend/index.html` ou `http://localhost:5500/frontend/` | public |

| Utilisateur | Mot de passe | Rôle |
|-------------|-------------|------|
| `api-user` | `proxy-secret-2026` | `ROLE_API` |
| `admin` | `admin-secret-2026` | `ROLE_ADMIN`, `ROLE_API` |

---

## Troubleshooting

### L'application ne démarre pas — erreur Kafka connection refused
Kafka n'est pas encore prêt. Vérifier le statut Docker :
```bash
docker-compose ps

#ou

docker ps --format "table {{.Names}}\t{{.Status}}"
```
Attendre que `proxy-kafka` soit `(healthy)` avant de lancer `mvn spring-boot:run`.

### Le frontend affiche "API indisponible"
L'API Spring Boot n'est pas accessible depuis l'origine du frontend. Vérifier :
1. Spring Boot tourne bien sur le port `8090` : `curl http://localhost:8090/actuator/health`
2. Ouvrir le frontend via `http://localhost:5500` (serveur local) plutôt que `file://`

### Kibana — visualisations vides ("No results found")
1. Vérifier que des données existent dans ES : `curl http://localhost:9200/exchange-rates/_count`
2. Si ES est vide : forcer un refresh (Étape 4)
3. Vérifier que la fenêtre temporelle du dashboard couvre la période des données

### Kibana — erreur 500 à l'import du NDJSON
Le NDJSON contient des `typeMigrationVersion` calibrés pour Kibana 8.11.0. Sur une autre version, l'import peut échouer. Vérifier : `curl -s http://localhost:5601/api/status | python -c "import sys,json; print(json.load(sys.stdin)['version']['number'])"`

### Tests unitaires — Lombok `cannot find symbol`
Vérifier la version Java et Lombok :
```bash
java --version          # doit être 17+
mvn dependency:tree | grep lombok   # doit afficher 1.18.36
```
Si Lombok < 1.18.36, vérifier que `<lombok.version>1.18.36</lombok.version>` est bien dans les `<properties>` du `pom.xml`.

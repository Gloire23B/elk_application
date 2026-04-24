# TP ELK — Guide complet d'exécution
# Basé sur le repo officiel : https://github.com/elomedah/tp-elk
# Version stack : 7.9.1

---

## BUGS CORRIGÉS DANS LE REPO ORIGINAL

| Fichier original | Bug | Correction |
|---|---|---|
| `logstash_short_sncf_with_filter.CONG` | Extension `.cong` invalide → Logstash ignore le fichier | Renommé `.conf` |
| `logstash_short_sncf_with_filter.cong` | `sincedb_path => "nul"` (Windows) | → `/dev/null` (Linux) |
| `logstash-sncf-elastic.conf` | `sincedb_path => "nul"` (Windows) | → `/dev/null` (Linux) |
| `logstash_sncf_conf` | Pas d'extension + `"ip_de_votre_elasticsearch"` non remplacé | Renommé `.conf` + host → `"elasticsearch"` |

---

## Structure du projet

```
tp-elk/
├── docker-compose.yml                         ← Stack ELK 7.9.1
├── tp-config/
│   ├── test.conf                              ← Test basique stdin→stdout
│   ├── logstash_short_sncf.conf               ← CSV court sans filtre
│   ├── logstash_short_sncf_with_filter.conf   ← CSV court + transformations corrigé
│   ├── logstash_sncf.conf                     ← CSV complet → corrigé
│   └── logstash-sncf-elastic.conf             ← CSV complet → corrigé
└── tp-data/
    ├── regularite-mensuelle-tgv-short.csv     ← 20 lignes réelles SNCF
    └── regularite-mensuelle-tgv.csv           ← 5000 lignes réelles SNCF (2011→2015)
```

---

## ÉTAPE 1 — Démarrer la stack ELK

```bash
# Depuis le dossier tp-elk/
docker-compose up -d

# Vérifier que les 3 services sont démarrés
docker-compose ps

# Attendre ~60s qu'Elasticsearch soit prêt
curl http://localhost:9200/_cluster/health?pretty
# Résultat attendu : "status" : "green" ou "yellow"
```

---

## ÉTAPE 2 — Copier les fichiers dans Logstash

```bash
docker cp tp-config logstash:/usr/share/logstash/tp-config
docker cp tp-data   logstash:/usr/share/logstash/tp-data

# Permissions (si nécessaire)
docker exec -u root logstash chown -R logstash:root /usr/share/logstash/tp-config
docker exec -u root logstash chown -R logstash:root /usr/share/logstash/tp-data
```

---

## ÉTAPE 3 — Vérification Logstash (test basique)

```bash
# Connexion au container
docker exec -it logstash bash

# Lancer test.conf
logstash -f /usr/share/logstash/tp-config/test.conf --path.data test
```

Taper du texte, observer l'enrichissement automatique par Logstash :
```json
{
      "@timestamp" => 2026-04-20T10:00:00.000Z,
         "message" => "bonjour",
        "@version" => "1",
            "host" => { "hostname" => "logstash" }
}
```

**Ce que signifie ce code :**
- `input { stdin{} }` → Logstash lit depuis le clavier
- `output { stdout{} }` → Logstash affiche dans le terminal
- Logstash enrichit chaque ligne avec @timestamp, host, @version

---

## ÉTAPE 4 — Analyse SNCF : lecture brute

```bash
# Toujours dans le container logstash
logstash -f /usr/share/logstash/tp-config/logstash_short_sncf.conf \
         --path.data sncf-short
```

**Réponses aux questions :**

**Quels sont les champs ?**
`date`, `axe`, `depart`, `arrivee`, `trains_programmes`, `trains_circules`,
`trains_annules`, `trains_retards`, `regularite`

**Quel est le type des données ?**
TOUT est en string par défaut. Elasticsearch ne peut pas faire
d'agrégations (moyenne, somme) sur des chaînes de caractères.

**La date est-elle au bon format ?**
NON. Format actuel : `"2011-09"` (YYYY-MM)
Elasticsearch requiert un format complet : `"2011-09-01"` (YYYY-MM-dd)

---

## ÉTAPE 5 — CSV SNCF avec filtres (transformations)

```bash
logstash -f /usr/share/logstash/tp-config/logstash_short_sncf_with_filter.conf \
         --path.data sncf-short-filter
```

Les transformations appliquées :
1. `mutate { replace => { "date" => "%{date}-01" } }` → "2011-09" devient "2011-09-01"
2. `date { match => ["date", "YYYY-MM-dd"] }` → converti en @timestamp
3. `mutate { convert => { "trains_retards" => "integer" } }` → cast numérique

---

## ÉTAPE 6 — Indexation dans Elasticsearch

```bash
logstash -f /usr/share/logstash/tp-config/logstash-sncf-elastic.conf \
         --path.data sncf
```

Vérification :
```bash
# Depuis votre machine (hors container)
curl http://localhost:9200/sncf/_count?pretty
# Résultat attendu : { "count": ~5000 }

curl http://localhost:9200/sncf/_search?pretty&size=2
```

---

## ÉTAPE 7 — Kibana : Visualisations

URL : **http://localhost:5601**

### 7.1 — Créer l'Index Pattern
```
Stack Management → Index Patterns → Create index pattern
Name: sncf
Time field: @timestamp
→ Create index pattern
```

### 7.2 — Ajuster la période
Par défaut Kibana affiche les 15 dernières minutes → **aucune donnée**.
Les données SNCF vont de 2011 à 2015.
→ Changer la plage : **Last 10 years** (ou Custom: 2011-01-01 → 2015-12-31)

### 7.3 — Camembert : gares avec le plus de retards
```
Visualize → Create → Pie
Index: sncf
Slice by : Terms → Field: depart.keyword → Top 10
Metric   : Sum → trains_retards
→ Update → Save "Retards par gare de départ"
```

### 7.4 — Courbe : évolution des retards dans le temps
```
Visualize → Create → Line
Y-axis : Average → regularite  (taux de régularité %)
X-axis : Date Histogram → @timestamp → Monthly
→ Save "Évolution régularité mensuelle"
```

### 7.5 — Dev Tools : requêtes directes
```
Menu → Dev Tools

GET /sncf/_count

GET /sncf/_search
{
  "size": 0,
  "aggs": {
    "par_axe": {
      "terms": { "field": "axe.keyword" },
      "aggs": {
        "regularite_moy": { "avg": { "field": "regularite" } }
      }
    }
  }
}
```

---

## Checklist de validation

| Critère | Commande de vérification |
|---|---|
| ES démarré | `curl http://localhost:9200` |
| Kibana démarré | Ouvrir http://localhost:5601 |
| Logstash test.conf | Taper texte → réponse JSON enrichie |
| CSV lu (short) | Logs Logstash affichent les champs SNCF |
| Date convertie | Champ `@timestamp` visible dans les logs |
| Numériques castés | `trains_retards` → integer dans les logs |
| Index ES créé | `curl http://localhost:9200/sncf/_count` |
| Index pattern Kibana | Visible dans Stack Management |
| Camembert créé | Visible dans Visualize Library |

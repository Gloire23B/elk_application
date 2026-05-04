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
        endpoints.put("GET  /api/exchange-rates/latest/{base}",                      "Derniers taux pour une devise (ex: USD, EUR)");
        endpoints.put("GET  /api/exchange-rates/rate?from=USD&to=EUR",               "Taux de conversion entre deux devises");
        endpoints.put("GET  /api/exchange-rates/history/{base}?fromDate=&toDate=",   "Historique des taux sur une période");
        endpoints.put("POST /api/exchange-rates/refresh/{base}",                     "Forcer un refresh depuis l'API externe");
        endpoints.put("GET  /actuator/health",                                       "Santé applicative (public)");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application",    "Exchange Rate Proxy");
        info.put("version",        "1.0.0");
        info.put("description",    "Proxy centralisé de taux de change — Kafka + Elasticsearch");
        info.put("authentication", "HTTP Basic — Utiliser les credentials fournis pour /api/**");
        info.put("endpoints",      endpoints);
        return info;
    }
}

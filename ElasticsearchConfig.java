package com.exchangerate.proxy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;

/**
 * Configuration du client Elasticsearch.
 *
 * <p>Décisions d'architecture :
 * <ul>
 *   <li>Timeout socket : 30s (requêtes d'agrégation complexes)</li>
 *   <li>Timeout connexion : 5s (fail-fast en cas d'indisponibilité)</li>
 * </ul>
 *
 * CORRECTION : Utilisation de {@link Environment} au lieu de {@code @Value}
 * pour éviter le problème d'injection tardive dans les classes qui étendent
 * {@link ElasticsearchConfiguration} — les champs @Value peuvent être null
 * lors de l'appel de clientConfiguration() au démarrage du contexte Spring.
 */
@Slf4j
@Configuration
@EnableElasticsearchRepositories(
        basePackages = "com.exchangerate.proxy.repository"
)
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Autowired
    private Environment env;

    /**
     * Configuration du client Elasticsearch.
     * Les propriétés sont lues via Environment pour garantir leur disponibilité.
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        String uri      = env.getProperty("spring.elasticsearch.uris", "http://localhost:9200");
        String username = env.getProperty("spring.elasticsearch.username", "elastic");
        String password = env.getProperty("spring.elasticsearch.password", "changeme");

        // Extraire host:port de l'URI (ex: "http://localhost:9200" → "localhost:9200")
        String hostAndPort = uri
                .replace("https://", "")
                .replace("http://", "");

        log.info("Connexion Elasticsearch: {}", hostAndPort);

        return ClientConfiguration.builder()
                .connectedTo(hostAndPort)
                .withBasicAuth(username, password)
                .withSocketTimeout(Duration.ofSeconds(30))
                .withConnectTimeout(Duration.ofSeconds(5))
                .build();
    }
}

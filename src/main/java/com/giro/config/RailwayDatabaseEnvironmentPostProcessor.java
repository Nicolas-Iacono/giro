package com.giro.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Toma las variables que Railway inyecta en el servicio de Postgres
 * (DATABASE_URL, PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD)
 * y las traduce al datasource de Spring. En local, sin esas variables,
 * sigue valiendo la URL de application.properties.
 */
public class RailwayDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return;
        }
        if (environment.getProperty("SPRING_DATASOURCE_URL") != null) {
            return;
        }
        Map<String, Object> mapped = mapear(environment);
        if (!mapped.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("railway-database", mapped));
        }
    }

    static Map<String, Object> mapear(ConfigurableEnvironment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            return desdeUrl(databaseUrl.trim());
        }
        String host = primero(environment, "PGHOST");
        if (host == null) {
            return Map.of();
        }
        String port = valor(environment, "PGPORT", "5432");
        String database = valor(environment, "PGDATABASE", valor(environment, "POSTGRES_DB", "railway"));
        String user = valor(environment, "PGUSER", valor(environment, "POSTGRES_USER", "postgres"));
        String password = valor(environment, "PGPASSWORD", valor(environment, "POSTGRES_PASSWORD", ""));
        Map<String, Object> map = new HashMap<>();
        map.put("spring.datasource.url", jdbc(host, port, database, host));
        map.put("spring.datasource.username", user);
        map.put("spring.datasource.password", password);
        return map;
    }

    private static Map<String, Object> desdeUrl(String databaseUrl) {
        if (databaseUrl.startsWith("jdbc:")) {
            return Map.of("spring.datasource.url", databaseUrl);
        }
        URI uri = URI.create(databaseUrl);
        String userInfo = uri.getUserInfo() == null ? "" : uri.getUserInfo();
        int separator = userInfo.indexOf(':');
        String user = separator < 0 ? userInfo : userInfo.substring(0, separator);
        String password = separator < 0 ? "" : userInfo.substring(separator + 1);
        String database = uri.getPath() == null ? "railway" : uri.getPath().replaceFirst("^/", "");
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        Map<String, Object> map = new HashMap<>();
        map.put("spring.datasource.url", jdbc(host, Integer.toString(port), database, host));
        if (!user.isBlank()) {
            map.put("spring.datasource.username", user);
        }
        map.put("spring.datasource.password", password);
        return map;
    }

    private static String jdbc(String host, String port, String database, String sslHost) {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        if (sslHost != null && (sslHost.contains("rlwy.net") || sslHost.contains("proxy.rlwy.net"))) {
            return url + "?sslmode=require";
        }
        return url;
    }

    private static String primero(ConfigurableEnvironment environment, String key) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valor(ConfigurableEnvironment environment, String key, String fallback) {
        String value = primero(environment, key);
        return value == null ? fallback : value;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

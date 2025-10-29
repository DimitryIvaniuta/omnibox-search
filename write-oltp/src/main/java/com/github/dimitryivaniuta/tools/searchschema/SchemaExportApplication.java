package com.github.dimitryivaniuta.tools.searchschema;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// excludes
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
//import org.springframework.boot.autoconfigure.kafka.KafkaStreamsAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Minimal Spring Boot app for schema export (no DB, no Kafka, no web server, no security).
 * Scans only GraphQL-safe config (scalars), so runtime beans (services/repos) are not loaded.
 */
@SpringBootApplication(
        scanBasePackages = {
                // only schema-safe config; DO NOT include your runtime packages here
                "com.github.dimitryivaniuta.gateway.write.graphql.safe",
                "org.springframework.graphql"
        },
        exclude = {
                DataSourceAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                KafkaAutoConfiguration.class,
//                KafkaStreamsAutoConfiguration.class,
                ServletWebServerFactoryAutoConfiguration.class,
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        }
)
public class SchemaExportApplication {

    public static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(SchemaExportApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.lazy-initialization=true")
                .run();
    }
}

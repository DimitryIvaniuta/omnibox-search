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
 * Minimal schema-only context for omnibox-search.
 * If you have custom scalars, point scanBasePackages to that package; otherwise scanning
 * Spring GraphQL infra is enough for SDL assembly from your *.graphqls files.
 */
@SpringBootApplication(
        scanBasePackages = {
                // include your safe scalars config package if you have one, e.g.:
                // "com.github.dimitryivaniuta.gateway.search.graphql.safe",
                "com.github.dimitryivaniuta.gateway.search.graphql.safe",
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

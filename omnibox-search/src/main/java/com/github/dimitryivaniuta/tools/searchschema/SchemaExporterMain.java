package com.github.dimitryivaniuta.tools.searchschema;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.graphql.execution.GraphQlSource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone entrypoint to print the GraphQL schema (SDL) for omnibox-search.
 * Usage: ./gradlew :omnibox-search:printSchema
 */
@Slf4j
public class SchemaExporterMain {
/*
    public static void main(String[] args) throws Exception {
        // Start Spring without the web server
        try (ConfigurableApplicationContext ctx = SpringApplication.run(
                OmniboxSearchApplication.class,
                "--spring.main.web-application-type=none"
        )) {
            GraphQlSource source = ctx.getBean(GraphQlSource.class);
            GraphQLSchema schema = source.schema();

            SchemaPrinter printer = new SchemaPrinter(
                    SchemaPrinter.Options.defaultOptions()
                            .includeScalarTypes(true)
                            .includeSchemaDefinition(true)
                            .includeIntrospectionTypes(false)
            );

            String sdl = printer.print(schema);

            Path out = Path.of("build/schema.graphqls");
            Files.createDirectories(out.getParent());
            Files.writeString(out, sdl);

            log.info("omnibox-search schema written to: {}", out.toAbsolutePath());
        }
    }*/
public static void main(String[] args) throws Exception {
    try (ConfigurableApplicationContext ctx = SchemaExportApplication.start()) {

        GraphQlSource source = ctx.getBean(GraphQlSource.class);
        GraphQLSchema schema = source.schema();

        SchemaPrinter printer = new SchemaPrinter(
                SchemaPrinter.Options.defaultOptions()
                        .includeScalarTypes(true)
                        .includeSchemaDefinition(true)
                        .includeIntrospectionTypes(false)
        );

        String sdl = printer.print(schema);

        Path out = Path.of("build/schema.graphqls");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sdl);

        System.out.println("write-oltp schema written to " + out.toAbsolutePath());
    }
}
}

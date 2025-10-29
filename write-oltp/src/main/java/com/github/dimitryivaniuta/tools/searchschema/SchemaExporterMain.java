package com.github.dimitryivaniuta.tools.searchschema;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.graphql.execution.GraphQlSource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entrypoint for Gradle task :write-oltp:printSchema.
 * Produces build/schema.graphqls for frontend codegen.
 */
public class SchemaExporterMain {

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

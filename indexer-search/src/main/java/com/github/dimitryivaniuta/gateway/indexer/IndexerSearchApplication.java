package com.github.dimitryivaniuta.gateway.indexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class IndexerSearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(IndexerSearchApplication.class, args);
    }
}
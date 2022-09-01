package dev.xethh.tools;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@SpringBootApplication
@EnableMongoRepositories
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class);
    }

    public @Bean MongoClient mongoClient() {
        return MongoClients.create();
    }

    @Bean
    ReactiveMongoTransactionManager transactionManager(ReactiveMongoDatabaseFactory rdbf) {
        return new ReactiveMongoTransactionManager(rdbf);
    }

    @Bean
    TransactionalOperator transactionOperator(ReactiveTransactionManager rtm) {
        return TransactionalOperator.create(rtm);
    }
}
package dev.xethh.tools;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import dev.xethh.libs.toolkits.commons.encryption.RSAFormatting;
import dev.xethh.libs.toolkits.commons.encryption.RsaEncryption;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SpringBootApplication
@EnableReactiveMongoRepositories
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class);
//        var keyPair = RsaEncryption.keyPair();
//        try {
//            new FileOutputStream(new File("pub.pem")).write(keyPair.getPublic().getEncoded());
//            new FileOutputStream(new File("pri.pem")).write(keyPair.getPrivate().getEncoded());
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        System.out.println("hi");
    }

    @Bean
    ReactiveMongoTransactionManager transactionManager(ReactiveMongoDatabaseFactory rdbf) {
        return new ReactiveMongoTransactionManager(rdbf);
    }

    @Bean
    TransactionalOperator transactionOperator(ReactiveTransactionManager rtm) {
        return TransactionalOperator.create(rtm);
    }

    @Bean
    public RouterFunction<ServerResponse> imgRouter() {
        return RouterFunctions
                .resources("/**", new ClassPathResource("public/"));
    }
}
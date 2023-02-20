package dev.xethh.tools.repo;

import dev.xethh.tools.entity.FileUpload;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UploadFileRepo extends ReactiveMongoRepository<FileUpload, String> {
    Flux<FileUpload> findAllByUserScopeAndPath(String userScope, String path);
}

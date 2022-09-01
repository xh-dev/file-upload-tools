package dev.xethh.tools.fileUploader.repo;

import dev.xethh.tools.fileUploader.entity.FileUpload;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface UploadFileRepo extends ReactiveMongoRepository<FileUpload, String> {
    Flux<FileUpload> findAllBySizeNotNull();
}

package dev.xethh.tools.repo;

import dev.xethh.tools.entity.FileUpload;
import dev.xethh.tools.entity.FileUploadConfirm;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface UploadFileConfirmRepo extends ReactiveMongoRepository<FileUploadConfirm, String> {
}

package dev.xethh.tools.fileUploader.repo;

import dev.xethh.tools.fileUploader.entity.FileUploadConfirm;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface UploadFileConfirmRepo extends ReactiveMongoRepository<FileUploadConfirm, String> {
}

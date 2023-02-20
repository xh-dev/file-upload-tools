package dev.xethh.tools;

import dev.xethh.tools.entity.FileUpload;
import dev.xethh.tools.repo.UploadFileRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Service
public class FileUploadService {
    @Autowired
    UploadFileRepo uploadFileRepo;


    public Mono<FileUpload> save(FileUpload fileUpload) {
        return uploadFileRepo
                .save(fileUpload)
                ;
    }
}

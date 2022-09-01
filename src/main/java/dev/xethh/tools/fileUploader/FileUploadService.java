package dev.xethh.tools.fileUploader;

import dev.xethh.tools.fileUploader.entity.FileUpload;
import dev.xethh.tools.fileUploader.entity.FileUploadConfirm;
import dev.xethh.tools.fileUploader.repo.UploadFileConfirmRepo;
import dev.xethh.tools.fileUploader.repo.UploadFileRepo;
import me.xethh.utils.functionalPacks.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class FileUploadService {
    @Autowired
    UploadFileRepo uploadFileRepo;
    @Autowired
    UploadFileConfirmRepo uploadFileConfirmRepo;

    @Transactional
    public Mono<FileUploadConfirm> confirm(FileUpload fileUpload) {
        return uploadFileRepo.delete(fileUpload).then(Mono.just(fileUpload))
                .flatMap(it -> {
                    FileUploadConfirm confirm = Scope.apply(new FileUploadConfirm(), fileUploadConfirm -> {
                        fileUploadConfirm.setFileName(it.getFileName());
                        fileUploadConfirm.setCode(it.getCode());
                        fileUploadConfirm.setHash(it.getHash());
                        fileUploadConfirm.setSize(it.getSize());
                        fileUploadConfirm.setOriginalId(it.getId());
                    });
                    return uploadFileConfirmRepo.save(confirm);
                })
                ;

    }
}

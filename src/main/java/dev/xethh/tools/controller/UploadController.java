package dev.xethh.tools.controller;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.xethh.tools.FileUploadService;
import dev.xethh.tools.entity.FileUpload;
import dev.xethh.tools.repo.UploadFileRepo;
import io.vavr.Tuple;
import io.vavr.control.Try;
import lombok.Data;
import me.xethh.utils.functionalPacks.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@RestController
@RequestMapping("api/upload")
public class UploadController {
    @Autowired
    ReactiveMongoTemplate template;

    @Autowired
    FileUploadService fileUploadService;

    @Autowired
    UploadFileRepo uploadFileRepo;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PostResponse.class, name = "PostResponse"),
            @JsonSubTypes.Type(value = ErrorResponse.class, name = "ErrorResponse")
    })
    public interface Response {
    }

    @Data
    public static class PostResponse implements Response {
        private String id;
        private String code;
        private Boolean success = true;
        private String fileName;
        private Long size;
        private String sha2;
        private String sha3;
        private Long revision;
    }

    @Data
    public static class ErrorResponse implements Response {
        private Boolean success = true;
        private String msg;
    }

    final String defaultTempDir = "./target/uploads/temp";
    Function<String, File> fileSupplier = (uuid ->
            Try.of(() -> new File(defaultTempDir).mkdirs())
                    .map(it ->
                            new File(String.format("%s/%s",defaultTempDir, uuid.toString()))
                    ).get()
    );

    final String defaultFinalDir = "./target/uploads/final";
    Function<String, File> finalFileSupplier = (objectId ->
            Try.of(() -> new File(defaultFinalDir).mkdirs())
                    .map(it ->
                            new File(String.format("%s/%s",defaultFinalDir, objectId))
                    ).get()
    );

    @Autowired
    TransactionalOperator transactionalOperator;

    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public Mono<Response> post(
            @RequestPart("code") String code,
            @RequestPart("file") Mono<FilePart> fileMono
    ) {
        final String tempUUID = UUID.randomUUID().toString();
        File file = fileSupplier.apply(tempUUID);

        MessageDigest digestSha2_256 = Try.of(() -> MessageDigest.getInstance("SHA-256")).get();
        MessageDigest digestSha3_256 = Try.of(() -> MessageDigest.getInstance("SHA3-256")).get();

        return transactionalOperator.transactional(
                fileMono
                        .flatMap(it -> {
                            FileChannel osChannel = Try.of(() -> new FileOutputStream(file).getChannel()).get();
                            AtomicLong cur = new AtomicLong(0);
                            return it.content().map(buffer ->
                                            Try.of(() -> {
                                                int length = buffer.asInputStream().available();
                                                ReadableByteChannel channelRead = Channels.newChannel(
                                                        new DigestInputStream(
                                                                new DigestInputStream(
                                                                        buffer.asInputStream(), digestSha2_256
                                                                ),
                                                                digestSha3_256
                                                        )
                                                );
                                                osChannel.transferFrom(channelRead, cur.get(), length);
                                                cur.addAndGet(length);
                                                return 1;
                                            }).get()).then(Mono.just(1))
                                    .map(rs -> it.filename())
                                    ;
                        })
                        .flatMap(filename ->
                                uploadFileRepo.findAllByCode(code)
                                        .reduce((x, b) ->
                                                x.getRevision() > b.getRevision() ? x : b
                                        )
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty())
                                        .map(latestOpt -> Tuple.of(filename, latestOpt))
                        )
                        .map(tuple -> {
                            var fileName = tuple._1;
                            var foundOpt = tuple._2;
                            String computedHash2 = HexFormat.of().formatHex(digestSha2_256.digest());
                            String computedHash3 = HexFormat.of().formatHex(digestSha3_256.digest());

                            FileUpload fu = Scope.apply(new FileUpload(), fileUpload -> {
                                fileUpload.setFileName(fileName);
                                fileUpload.setFileUuidCode(tempUUID.toString());
                                fileUpload.setCode(code);
                                fileUpload.setSha2(computedHash2);
                                fileUpload.setSha3(computedHash3);
                                fileUpload.setSize(file.length());
                                fileUpload.setRevision(0L);
                            });
                            if (foundOpt.isPresent()) {
                                fu.setRevision(foundOpt.get().getRevision() + 1);
                                var found = foundOpt.get();
                                if (found.getSha2().equals(computedHash2) && found.getSha3().equals(computedHash3)) {
                                    fu = found;
                                    return fu;
                                }
                            }
                            return fu;
                        })
                        .flatMap(fu -> {
                            var response = Scope.apply(new PostResponse(), postResponse1 -> {
                                postResponse1.setFileName(fu.getFileName());
                                postResponse1.setSize(fu.getSize());
                                postResponse1.setSha2(fu.getSha2());
                                postResponse1.setSha3(fu.getSha3());
                            });

                            if (fu.getId() == null) {
                                return fileUploadService.save(fu)
                                        .map(up -> {
                                            if(!fileSupplier.apply(up.getFileUuidCode())
                                                    .renameTo(
                                                            finalFileSupplier.apply(fu.getId()))
                                            ){
                                                throw new RuntimeException("Fail to rename the file");
                                            }
                                            response.setId(up.getId());
                                            response.setRevision(up.getRevision());
                                            response.setCode(up.getCode());
                                            return (Response) response;
                                        });
                            } else {
                                if( !finalFileSupplier.apply(fu.getId()).exists()){
                                    if(!fileSupplier.apply(fu.getFileUuidCode())
                                            .renameTo(
                                                    finalFileSupplier.apply(fu.getId()))
                                    ){
                                        throw new RuntimeException("Fail to rename the file");
                                    }
                                } else {
                                    if( ! fileSupplier.apply(tempUUID).delete() )
                                        return Mono.error(new RuntimeException("Fail to delete temp file"));
                                }
                                response.setId(fu.getId());
                                response.setRevision(fu.getRevision());
                                response.setCode(fu.getCode());
                                return Mono.just((Response) response);
                            }
                        })
                        .onErrorResume(it -> Mono.just(Scope.apply(new ErrorResponse(), postResponse1 -> {
                            it.printStackTrace();
                            postResponse1.setMsg(it.getMessage());
                        })))
        )
                ;
    }
}

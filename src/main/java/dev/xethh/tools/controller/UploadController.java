package dev.xethh.tools.controller;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.xethh.tools.FileUploadService;
import dev.xethh.tools.config.CustConfig;
import dev.xethh.tools.entity.FileUpload;
import dev.xethh.tools.jwt.JwtService;
import dev.xethh.tools.repo.UploadFileRepo;
import dev.xethh.tools.utils.FileUtils;
import dev.xethh.tools.utils.PathUtils;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import lombok.Data;
import me.xethh.utils.functionalPacks.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@RestController
@RequestMapping("api/upload/{user-scope}")
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
        private UploadType uploadType;
        private String userScope;
        private String path;
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

    @Autowired
    CustConfig custConfig;
    //    final String defaultTempDir = "./target/uploads/temp";
    Function<String, File> fileSupplier = (uuid ->
            Try.of(() -> new File(custConfig.tempDir()).mkdirs())
                    .map(it ->
                            new File(String.format("%s/%s", custConfig.tempDir(), uuid))
                    ).get()
    );

    //    final String defaultFinalDir = "./target/uploads/final";
    Function<String, File> finalFileSupplier = (objectId ->
            Try.of(() -> new File(custConfig.finalDir()).mkdirs())
                    .map(it ->
                            new File(String.format("%s/%s", custConfig.finalDir(), objectId))
                    ).get()
    );

    @Autowired
    TransactionalOperator transactionalOperator;

    static final String prefix = "Bearer";
    static final String sha2Algo = "SHA-256";
    static final String sha3Algo = "SHA3-256";


    public enum UploadType {
        INSERT, NO_CHANGE, MODIFY_FILE, MODIFY_FIELD
    }

    public record PostContextStage1(File tempFilePath, MessageDigest sha2Digest, MessageDigest sh3Digest,
                                    FilePart filePart, String userScope, String path) {
    }

    public record PostContextStage2(File tempFilePath, String sha2, String sha3, String userScope, String path) {
    }

    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE}, consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public Mono<Response> post(
            @RequestHeader("Authorization") String authorization,
            @RequestPart("path") String path,
            @RequestPart("file") Mono<FilePart> fileMono,
            @PathVariable("user-scope") String userScope
    ) {

        return Mono.just(userScope)
                .filter(it -> it != null && !it.equals(""))
                .switchIfEmpty(Mono.error(new RuntimeException("User scope is empty")))
                .map(it -> path)
                .filter(it -> it != null && !it.equals(""))
                .switchIfEmpty(Mono.error(new RuntimeException("Path is empty")))
                .filter(PathUtils::isUnixFilePath)
                .switchIfEmpty(Mono.error(new RuntimeException("Path is not unix file path")))
                .map(it -> authorization)
                .filter(authStr -> authStr != null && !authStr.equals(""))
                .switchIfEmpty(Mono.error(new RuntimeException("Authorization is empty")))
                .filter(authStr -> authStr.startsWith(prefix))
                .switchIfEmpty(Mono.error(new RuntimeException("Authorization is not Bearer")))
                .map(authStr -> authStr.split(" "))
                .filter(authorizationArray -> authorizationArray.length == 2)
                .switchIfEmpty(Mono.error(new RuntimeException("Authorization not expected format")))
                .map(authorizationArray -> authorizationArray[1])
                .filter(token -> jwtService.verify(token, JwtService.SCOPE, userScope).isPresent())
                .switchIfEmpty(Mono.error(new RuntimeException("Authorization is not valid")))
                .flatMap(it -> fileMono)
                .map(filePart ->
                        new PostContextStage1(
                                fileSupplier.apply(UUID.randomUUID().toString()),
                                Try.of(() -> MessageDigest.getInstance(sha2Algo)).get(),
                                Try.of(() -> MessageDigest.getInstance(sha3Algo)).get(),
                                filePart,
                                userScope,
                                path
                        )
                )
                .flatMap(context -> {
                    FileChannel osChannel = Try.of(() -> new FileOutputStream(context.tempFilePath).getChannel()).get();
                    AtomicLong cur = new AtomicLong(0);
                    return context.filePart.content().reduce(context, (noUsed, buffer) -> {
                                var inStream = buffer.asInputStream();
                                var length = Try.of(() -> inStream.available()).get();
                                var channelRead = Channels.newChannel(
                                        new DigestInputStream(
                                                new DigestInputStream(inStream, context.sha2Digest),
                                                context.sh3Digest
                                        )
                                );
                                FileUtils.transfer(osChannel, channelRead, length, cur);
                                return noUsed;
                            })
                            .then(Mono.just(context))
                            .map(self -> {
                                return Try.of(() -> {
                                    osChannel.close();
                                    return new PostContextStage2(
                                            self.tempFilePath,
                                            HexFormat.of().formatHex(self.sha2Digest.digest()),
                                            HexFormat.of().formatHex(self.sh3Digest.digest()),
                                            self.userScope,
                                            self.path
                                    );
                                }).get();
                            })
                            ;
                })
                .flatMap(context -> {
                    return transactionalOperator.transactional(
                            uploadFileRepo.findAllByUserScopeAndPath(context.userScope(), context.path)
                                    .reduce(Tuple.<FileUpload, FileUpload, Long>of(null, null, 0L), (a, b) -> {
                                        var topRecord = a._1;
                                        var originalFile = a._2;
                                        var maxNum = a._3;

                                        return Tuple.of(
                                                topRecord == null ? b : (topRecord.getRevision() > b.getRevision() ? topRecord : b),
                                                Optional.of(b)
                                                        .filter(it -> it.getSha2().equals(context.sha2()))
                                                        .filter(it -> it.getSha3().equals(context.sha3()))
                                                        .filter(it -> it.getSize().equals(context.tempFilePath.length()))
                                                        .map(it -> {
                                                                    if (originalFile == null) {
                                                                        return it;
                                                                    } else {
                                                                        return originalFile.getRevision() > it.getRevision() ? it : originalFile;
                                                                    }
                                                                }
                                                        ).orElse(null)
                                                , Math.max(b.getRevision(), maxNum)
                                        );
                                    })
                                    .<Tuple2<UploadType, FileUpload>>flatMap(tuple -> {
                                        var topRecord = tuple._1;
                                        var originalFile = tuple._2;
                                        var maxNum = tuple._3;

                                        if (topRecord == null) {
                                            return uploadFileRepo.save(
                                                            new FileUpload(
                                                                    null,
                                                                    context.userScope(),
                                                                    context.path(),
                                                                    context.sha2(),
                                                                    context.sha3(),
                                                                    1L,
                                                                    context.tempFilePath().length(),
                                                                    ""
                                                            )
                                                    )
                                                    .flatMap(fu -> {
                                                        fu.setRefFile(fu.getId());
                                                        return uploadFileRepo.save(fu);
                                                    })
                                                    .map(fu -> Tuple.of(UploadType.INSERT, fu))
                                                    ;
                                        } else {
                                            if (originalFile.getRevision() == maxNum) {
                                                return Mono.just(Tuple.of(UploadType.NO_CHANGE, originalFile));
                                            } else {
                                                return uploadFileRepo.save(
                                                                new FileUpload(
                                                                        null,
                                                                        context.userScope,
                                                                        context.path,
                                                                        context.sha2,
                                                                        context.sha3,
                                                                        maxNum + 1L,
                                                                        context.tempFilePath.length(),
                                                                        Optional.ofNullable(originalFile).map(FileUpload::getRefFile).orElse("")
                                                                )
                                                        )
                                                        .flatMap(fu -> {
                                                            if (originalFile == null) {
                                                                return Mono.just(fu)
                                                                        .thenReturn(Tuple.of(UploadType.MODIFY_FIELD, fu));
                                                            } else {
                                                                fu.setRefFile(fu.getId());
                                                                return uploadFileRepo.save(fu)
                                                                        .map(fu1 -> Tuple.of(UploadType.MODIFY_FILE, fu1));
                                                            }
                                                        })
                                                        ;
                                            }
                                        }
                                    })
                                    .doOnNext(pair -> {
                                        var uploadType = pair._1;
                                        var fu = pair._2;
                                        Try.run(() -> {
                                                    switch (uploadType) {
                                                        case INSERT:
                                                        case MODIFY_FILE:
                                                            FileUtils.moveFile(context.tempFilePath, finalFileSupplier.apply(fu.getId()));
                                                            break;
                                                        case MODIFY_FIELD:
                                                        case NO_CHANGE:
                                                            FileUtils.deleteFile(context.tempFilePath);
                                                            break;
                                                    }
                                                }
                                        ).get();
                                    })
                    );
                })
                .map(context -> {
                    var uploadType = context._1;
                    var fu = context._2;
                    var response = Scope.apply(new PostResponse(), postResponse1 -> {
                        postResponse1.setFileName(fu.getPath());
                        postResponse1.setSize(fu.getSize());
                        postResponse1.setSha2(fu.getSha2());
                        postResponse1.setSha3(fu.getSha3());

                        postResponse1.setId(fu.getId());
                        postResponse1.setRevision(fu.getRevision());
                        postResponse1.setPath(fu.getPath());
                        postResponse1.setPath(fu.getPath());
                        postResponse1.setUserScope(fu.getUserScope());
                    });

                    return (Response) response;
                })
                .onErrorResume(it -> Mono.just(Scope.apply(new ErrorResponse(), postResponse1 -> {
                    it.printStackTrace();
                    postResponse1.setMsg(it.getMessage());
                })))
                ;


//        var us = jwtService.getToken(userScope);
//        System.out.println(String.format("Bearer %s", us));
    }

    @Autowired
    JwtService jwtService;
}

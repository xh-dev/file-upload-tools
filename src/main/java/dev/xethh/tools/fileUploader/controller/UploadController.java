package dev.xethh.tools.fileUploader.controller;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mongodb.client.model.changestream.OperationType;
import dev.xethh.tools.fileUploader.FileUploadService;
import dev.xethh.tools.fileUploader.entity.FileUpload;
import dev.xethh.tools.fileUploader.repo.UploadFileRepo;
import io.vavr.Tuple;
import io.vavr.control.Try;
import lombok.Data;
import me.xethh.utils.functionalPacks.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
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
    @PostConstruct
    public void postConstruct(){
        template.changeStream(FileUpload.class)
                .watchCollection("uploaded")
                .listen()
                .subscribeOn(Schedulers.boundedElastic())
                .filter(it->OperationType.INSERT.equals(it.getRaw().getOperationType()))
                .zipWith(Flux.range(0, 999999999), (event, index)-> Tuple.of(index, event.getBody()))
                .map(it->{
                    Integer index = it._1;
                    FileUpload obj = it._2;
                    System.out.println("Index "+index+" - "+obj);
                    return obj;
                })
                .flatMap(it->fileUploadService.confirm(it))
                .subscribe();



        Flux.interval(Duration.of(1, ChronoUnit.MINUTES))
                .subscribeOn(Schedulers.boundedElastic())
                .map(it->{
                    System.out.println("Round "+it);
                    return it;
                })
                .flatMap(it->uploadFileRepo.findAll())
                .flatMap(it->fileUploadService.confirm(it))
                .subscribe();
    }

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
        private Boolean success = true;
        private String fileName;
        private Long size;
        private String hash;
    }

    @Data
    public static class ErrorResponse implements Response {
        private Boolean success = true;
        private String msg;
    }
    Function<UUID, File> fileSupplier = (uuid ->
            Try.of(() -> new File("./target/uploads").mkdirs())
                    .map(it ->
                            new File(String.format("./target/uploads/%s", uuid.toString()))
                    ).get()
    );

    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public Mono<Response> post(
            @RequestPart("code") String code,
            @RequestPart("file") Mono<FilePart> fileMono
    ) {
        File file = fileSupplier.apply(UUID.randomUUID());

        MessageDigest digestSha256 = Try.of(() -> MessageDigest.getInstance("SHA-256")).get();

        return fileMono
                .flatMap(it -> {
                    FileChannel osChannel = Try.of(() -> new FileOutputStream(file).getChannel()).get();
                    AtomicLong cur = new AtomicLong(0);
                    return it.content().map(buffer ->
                                    Try.of(() -> {
                                        int length = buffer.asInputStream().available();
                                        ReadableByteChannel channelRead = Channels.newChannel(new DigestInputStream(buffer.asInputStream(), digestSha256));
                                        osChannel.transferFrom(channelRead, cur.get(), length);
                                        cur.addAndGet(length);
                                        return 1;
                                    }).get()).then(Mono.just(1))
                            .map(rs -> it.filename())
                            ;
                })
                .map(fileName -> {
                    String computedHash = HexFormat.of().formatHex(digestSha256.digest());
                    return Scope.apply(new PostResponse(), postResponse1 -> {
                        postResponse1.setFileName(fileName);
                        postResponse1.setSize(file.length());
                        postResponse1.setHash(computedHash);
                    });
                })
                .flatMap(it->{
                    FileUpload fu = Scope.apply(new FileUpload(), fileUpload -> {
                        fileUpload.setFileName(it.fileName);
                        fileUpload.setCode(code);
                        fileUpload.setHash(it.hash);
                        fileUpload.setSize(it.size);
                    });
                    return uploadFileRepo.save(fu).map(up->{
                        it.setId(up.getId());
                        return (Response) it;
                    });
                })
                .onErrorResume(it -> Mono.just(Scope.apply(new ErrorResponse(), postResponse1 -> {
                    it.printStackTrace();
                    postResponse1.setMsg(it.getMessage());
                })))
                ;
    }
}

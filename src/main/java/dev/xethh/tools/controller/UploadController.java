package dev.xethh.tools.controller;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xethh.utils.WrappedResult.wrappedResult.WrappedResult;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.Builder;
import lombok.Data;
import me.xethh.utils.functionalPacks.Scope;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.*;
import java.lang.annotation.Repeatable;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@RestController
@RequestMapping("api/upload")
public class UploadController {

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
                    return (Response) Scope.apply(new PostResponse(), postResponse1 -> {
                        postResponse1.setFileName(fileName);
                        postResponse1.setSize(file.length());
                        postResponse1.setHash(computedHash);
                    });
                })
                .onErrorResume(it -> Mono.just(Scope.apply(new ErrorResponse(), postResponse1 -> {
                    postResponse1.setMsg(it.getMessage());
                })))
                ;
    }
}

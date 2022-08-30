package dev.xethh.tools.controller;

import dev.xethh.utils.WrappedResult.wrappedResult.WrappedResult;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.Builder;
import lombok.Data;
import me.xethh.utils.functionalPacks.Scope;
import org.apache.commons.codec.digest.DigestUtils;
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

    @Data
    public static class PostResponse{
        private Boolean success = true;
        private String msg;
        private String fileName;
        private Long size;
        private String hash;
    }


    Function<UUID, File> fileSupplier = (uuid -> {
        new File("./target/uploads").mkdirs();
        return new File(String.format("./target/uploads/%s", uuid.toString()));
    });

    @PostMapping
    public Mono<PostResponse> post(
            @RequestPart("hash") String hash,
            @RequestPart("size") String size,
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
                    if (Long.parseLong(size) != file.length()) {
                        RuntimeException ex = new RuntimeException(String.format("File size %d not match pushed file size %s", file.length(), size));
                        ex.printStackTrace();
                        throw new RuntimeException("Meta data not match", ex);
                    }
                    String computedHash = HexFormat.of().formatHex(digestSha256.digest());
                    if(!computedHash.equals(hash)){
                        RuntimeException ex = new RuntimeException(String.format("File has %s not match pushed file hash %s", computedHash, hash));
                        throw new RuntimeException("Meta data not match", ex);
                    }
                    return Scope.apply(new PostResponse(), postResponse1 -> {
                        postResponse1.setFileName(fileName);
                        postResponse1.setSize(file.length());
                        postResponse1.setHash(computedHash);
                    });
                })
                .onErrorResume(it -> Mono.just(Scope.apply(new PostResponse(), postResponse1 -> {
                    postResponse1.setMsg(it.getMessage());
                })));
    }
}

package dev.xethh.tools.controller;

import dev.xethh.utils.WrappedResult.wrappedResult.WrappedResult;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.Data;
import me.xethh.utils.functionalPacks.Scope;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("api/upload")
public class UploadController {
    @GetMapping("")
    public Mono<String> get(){
        return Mono.just("hi");
    }


    @Data
    public static class PostResponse{
        private Boolean success;
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
    ){
        File file = fileSupplier.apply(UUID.randomUUID());
        PostResponse postResponse = new PostResponse();

        return fileMono
                .flatMap(it->
                    it.transferTo(file).then(Mono.just(it.filename()))
                )
                .map(fileName -> {
                    if (Long.parseLong(size) != file.length()) {
                        throw new RuntimeException("Meta data not match");
                    }

                    String hex = Try.of(() -> DigestUtils.sha256Hex(new FileInputStream(file))).get();
                    System.out.println(hex);
                    System.out.println(hash);

                    return Scope.apply(postResponse, postResponse1 -> {
                        postResponse1.setFileName(fileName);
                        postResponse1.setSize(file.length());
                        postResponse1.setHash(hex);
                        postResponse.setSuccess(true);
                    });
                })
                .onErrorResume(it -> Mono.just(Scope.apply(postResponse, postResponse1 -> {
                    postResponse1.setSuccess(false);
                })));
    }
}

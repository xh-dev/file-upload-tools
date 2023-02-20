package dev.xethh.tools.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("docker")
@Component
@Slf4j
public class DockerConfig implements CustConfig {
    @PostConstruct
    public void init() {
        log.info("Docker config loaded");
    }

    @Override
    public String tempDir() {
        return "/data/files";
    }

    @Override
    public String finalDir() {
        return "/data/final";
    }

    @Override
    public String keyPairDir() {
        return "/keyPair";
    }
}

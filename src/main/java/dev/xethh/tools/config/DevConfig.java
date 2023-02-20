package dev.xethh.tools.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dev")
@Component
@Slf4j
public class DevConfig implements CustConfig {
    @PostConstruct
    public void init(){
        log.info("Docker config loaded");
    }

    @Override
    public String tempDir() {
        return "./files";
    }

    @Override
    public String finalDir() {
        return "./final";
    }

    @Override
    public String keyPairDir() {
        return "../";
    }
}

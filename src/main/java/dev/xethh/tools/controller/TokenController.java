package dev.xethh.tools.controller;

import dev.xethh.tools.jwt.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("api/token/{user}")
public class TokenController {
    @Autowired
    JwtService jwtService;

    @GetMapping
    public Mono<String> get(@PathVariable String user, @RequestParam(required = false, name = "valid-for-days") Integer validForDays){
        return Mono.just(user)
                .filter(u->u!=null)
                .map(u->u.trim())
                .filter(u->!u.isEmpty())
                .switchIfEmpty(Mono.error(new RuntimeException("User is empty")))
                .map(u->jwtService.getToken(u, Optional.ofNullable(validForDays)))
                ;
    }
}

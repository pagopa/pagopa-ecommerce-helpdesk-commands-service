package it.pagopa.helpdeskcommands.controllers

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class DummyController {
    @GetMapping("/hello")
    fun hello(): Mono<ResponseEntity<String>> {
        return Mono.just(ResponseEntity.ok("Hello"))
    }
}
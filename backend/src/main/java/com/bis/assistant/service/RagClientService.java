package com.bis.assistant.service;

import com.bis.assistant.dto.ChatDtos.*;
import com.bis.assistant.exception.RagServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * HTTP client for the Python FastAPI RAG service.
 * Supports both blocking (single response) and streaming (SSE) modes.
 */
@Service
@Slf4j
public class RagClientService {

    private final WebClient webClient;
    private final Duration timeout;

    public RagClientService(
            WebClient.Builder builder,
            @Value("${rag.service-url}") String ragUrl,
            @Value("${rag.timeout-seconds:60}") long timeoutSeconds) {

        this.webClient = builder
            .baseUrl(ragUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /** Blocking query — returns full RagResponse */
    public RagResponse query(RagRequest request) {
        log.debug("RAG query: intent={} lang={}", request.lang(), request.lang());
        try {
            return webClient.post()
                .uri("/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RagResponse.class)
                .timeout(timeout)
                .block();
        } catch (WebClientResponseException e) {
            log.error("RAG service error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RagServiceException("RAG service returned " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("RAG service unreachable", e);
            throw new RagServiceException("RAG service unavailable", e);
        }
    }

    /** Streaming query — returns Flux of SSE token strings */
    public Flux<String> streamQuery(RagRequest request) {
        return webClient.post()
            .uri("/rag/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(String.class)
            .timeout(timeout)
            .onErrorResume(e -> {
                log.error("Stream error", e);
                return Flux.just("[ERROR] " + e.getMessage());
            });
    }
}

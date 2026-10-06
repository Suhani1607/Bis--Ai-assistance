package com.bis.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    /**
     * ObjectMapper with JavaTimeModule for OffsetDateTime serialisation.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * WebClient.Builder with a 10 MB response buffer
     * (needed for large BIS PDF metadata responses).
     */
    @Bean
    public WebClient.Builder webClientBuilder(ObjectMapper objectMapper) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(c -> {
                c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10 MB
                c.defaultCodecs().jackson2JsonEncoder(
                    new Jackson2JsonEncoder(objectMapper));
                c.defaultCodecs().jackson2JsonDecoder(
                    new Jackson2JsonDecoder(objectMapper));
            })
            .build();

        return WebClient.builder().exchangeStrategies(strategies);
    }
}

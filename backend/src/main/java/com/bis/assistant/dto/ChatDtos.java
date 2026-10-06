package com.bis.assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


public class ChatDtos {


    public record SendMessageRequest(
        @NotBlank(message = "Message cannot be blank")
        @Size(max = 4000, message = "Message too long")
        String content,

        /** Optional: pass to continue an existing conversation */
        UUID conversationId,

        /** en | hi */
        String lang,

        /** true = stream SSE tokens */
        boolean stream
    ) {}

    public record FeedbackRequest(
        /** -1 = thumbs down, 1 = thumbs up */
        int rating,
        String comment
    ) {
        @AssertTrue(message = "Rating must be -1 or 1")
        public boolean hasValidRating() {
            return rating == -1 || rating == 1;
        }
    }

    // ── Outbound ─────────────────────────────────────────────

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessageResponse(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        String contentHi,
        List<CitationDto> citations,
        Integer tokensUsed,
        Integer latencyMs,
        OffsetDateTime createdAt
    ) {}

    @Builder
    public record CitationDto(
        String chunkId,
        String isNumber,
        String clauseRef,
        String sectionTitle,
        String excerpt,
        Float relevanceScore,
        int sortOrder
    ) {}

    @Builder
    public record ConversationSummary(
        UUID id,
        String title,
        String lang,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int messageCount
    ) {}

    @Builder
    public record ConversationDetail(
        UUID id,
        String title,
        String lang,
        List<MessageResponse> messages,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {}

    // ── RAG service contract ──────────────────────────────────
    // Used internally to call the Python RAG service

    @Builder
    public record RagRequest(
        String query,
        String lang,
        List<RagMessage> history,
        int maxResults,
        boolean includeHindi
    ) {}

    public record RagMessage(String role, String content) {}

    @Builder
    public record RagResponse(
        String answer,
        String answerHi,
        List<RagChunk> chunks,
        String detectedIntent,
        String detectedLang,
        int tokensUsed,
        int latencyMs
    ) {}

    @Builder
    public record RagChunk(
        String chunkId,
        String isNumber,
        String clauseRef,
        String sectionTitle,
        String excerpt,
        float score
    ) {}
}

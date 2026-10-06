package com.bis.assistant.controller;

import com.bis.assistant.dto.ChatDtos.*;
import com.bis.assistant.security.UserPrincipal;
import com.bis.assistant.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * POST /chat/message          — send a message, get a full response
 * POST /chat/message/stream   — send a message, stream SSE tokens
 * GET  /chat/conversations    — list user's conversations
 * GET  /chat/conversations/{id} — get full conversation with messages
 * DELETE /chat/conversations/{id} — archive conversation
 * POST /chat/messages/{id}/feedback — submit thumbs rating
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ChatController {

    private final ChatService chatService;

    // ── Send message (blocking) ──────────────────────────────

    @PostMapping("/message")
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("Chat message from user={} conv={}", principal.id(), request.conversationId());
        MessageResponse response = chatService.processMessage(request, principal.id());
        return ResponseEntity.ok(response);
    }

    // ── Send message (SSE streaming) ─────────────────────────

    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMessage(
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("Streaming chat from user={}", principal.id());
        return chatService.streamMessage(request, principal.id());
    }

    // ── Conversation list ────────────────────────────────────

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummary>> listConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        List<ConversationSummary> list = chatService.listConversations(principal.id(), page, size);
        return ResponseEntity.ok(list);
    }

    // ── Conversation detail ──────────────────────────────────

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationDetail> getConversation(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        ConversationDetail detail = chatService.getConversation(id, principal.id());
        return ResponseEntity.ok(detail);
    }

    // ── Archive conversation ─────────────────────────────────

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> archiveConversation(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        chatService.archiveConversation(id, principal.id());
        return ResponseEntity.noContent().build();
    }

    // ── Feedback ─────────────────────────────────────────────

    @PostMapping("/messages/{messageId}/feedback")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable UUID messageId,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        chatService.saveFeedback(messageId, request, principal.id());
        return ResponseEntity.ok().build();
    }
}

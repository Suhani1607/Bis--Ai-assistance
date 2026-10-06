package com.bis.assistant.service;

import com.bis.assistant.dto.ChatDtos.*;
import com.bis.assistant.exception.ResourceNotFoundException;
import com.bis.assistant.exception.UnauthorizedException;
import com.bis.assistant.model.*;
import com.bis.assistant.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final UserRepository userRepo;
    private final FeedbackRepository feedbackRepo;
    private final RagClientService ragClient;
    private final QueryAnalyticsService analyticsService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_HISTORY_MESSAGES = 10;

    // ── Process (blocking) ────────────────────────────────────

    @Transactional
    public MessageResponse processMessage(SendMessageRequest request, UUID userId) {
        long start = Instant.now().toEpochMilli();

        User user = userRepo.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Resolve or create conversation
        Conversation conversation = resolveConversation(request.conversationId(), user, request.lang());

        // Persist user message
        Message userMsg = persistMessage(conversation, "user", request.content(), null);

        // Build history for RAG (latest 10 messages in chronological order)
        List<RagMessage> history = buildHistory(conversation, userMsg.getId());

        // Call RAG service
        String effectiveLang = request.lang() != null ? request.lang() : user.getPreferredLang();
        RagResponse ragResponse = ragClient.query(RagRequest.builder()
            .query(request.content())
            .lang(effectiveLang)
            .history(history)
            .maxResults(8)
            .includeHindi("hi".equals(effectiveLang))
            .build());

        // Persist assistant message
        Message assistantMsg = persistMessage(conversation, "assistant",
            ragResponse.answer(), ragResponse.answerHi());
        assistantMsg.setTokensUsed(ragResponse.tokensUsed());
        assistantMsg.setLatencyMs((int)(Instant.now().toEpochMilli() - start));

        // Persist citations
        List<Citation> citations = persistCitations(assistantMsg, ragResponse.chunks());
        assistantMsg.setCitations(citations);

        // Auto-title conversation from first user message
        if (conversation.getTitle() == null) {
            String title = request.content().length() > 80
                ? request.content().substring(0, 77) + "..."
                : request.content();
            conversation.setTitle(title);
        }
        conversation.setUpdatedAt(OffsetDateTime.now());
        conversationRepo.save(conversation);

        // Log query analytics (async, best-effort)
        analyticsService.logQuery(new QueryAnalyticsService.QueryLog(
            request.content(),
            ragResponse.detectedIntent(),
            ragResponse.detectedLang(),
            null,
            ragResponse.chunks() != null ? ragResponse.chunks().size() : 0,
            ragResponse.chunks() != null && !ragResponse.chunks().isEmpty() ? ragResponse.chunks().get(0).score() : 0f,
            (int)(Instant.now().toEpochMilli() - start),
            ragResponse.chunks() == null || ragResponse.chunks().isEmpty(),
            userId
        ));

        return toMessageResponse(assistantMsg);
    }

    // ── Stream ────────────────────────────────────────────────

    public Flux<String> streamMessage(SendMessageRequest request, UUID userId) {
        long start = Instant.now().toEpochMilli();

        User user = userRepo.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = resolveConversation(request.conversationId(), user, request.lang());
        persistMessage(conversation, "user", request.content(), null);
        List<RagMessage> history = buildHistory(conversation, null);

        String lang = request.lang() != null ? request.lang() : user.getPreferredLang();

        StringBuilder assistantContent = new StringBuilder();
        List<RagChunk> retrievedChunks = new CopyOnWriteArrayList<>();
        UUID conversationId = conversation.getId();

        return ragClient.streamQuery(RagRequest.builder()
            .query(request.content())
            .lang(lang)
            .history(history)
            .maxResults(8)
            .includeHindi("hi".equals(lang))
            .build())
            .doOnNext(chunk -> processStreamChunk(chunk, assistantContent, retrievedChunks))
            .doOnComplete(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    finalizeStreamedMessage(
                        conversationId,
                        request.content(),
                        assistantContent.toString(),
                        retrievedChunks,
                        (int)(Instant.now().toEpochMilli() - start),
                        userId
                    );
                });
            });
    }

    private void processStreamChunk(String chunk, StringBuilder content, List<RagChunk> citations) {
        if (chunk == null || chunk.isBlank()) return;
        for (String line : chunk.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String data = line.startsWith("data:") ? line.substring(5).trim() : line;
            if (data.equals("[DONE]") || data.isEmpty()) continue;
            try {
                JsonNode node = objectMapper.readTree(data);
                String type = node.path("type").asText();
                if ("token".equals(type)) {
                    content.append(node.path("data").asText());
                } else if ("citations".equals(type)) {
                    JsonNode items = node.path("data");
                    if (items.isArray()) {
                        for (JsonNode item : items) {
                            citations.add(RagChunk.builder()
                                .chunkId(item.path("chunk_id").asText())
                                .isNumber(item.hasNonNull("is_number") ? item.path("is_number").asText() : null)
                                .clauseRef(item.hasNonNull("clause_ref") ? item.path("clause_ref").asText() : null)
                                .sectionTitle(item.hasNonNull("section_title") ? item.path("section_title").asText() : null)
                                .excerpt(item.hasNonNull("excerpt") ? item.path("excerpt").asText() : null)
                                .score((float) item.path("score").asDouble())
                                .build());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Non-JSON SSE chunk ignored: {}", data);
            }
        }
    }

    private void finalizeStreamedMessage(
            UUID conversationId, String userQuery, String assistantResponse,
            List<RagChunk> chunks, int latencyMs, UUID userId) {
        Conversation conversation = conversationRepo.findById(conversationId).orElse(null);
        if (conversation == null) return;

        Message assistantMsg = persistMessage(conversation, "assistant", assistantResponse, null);
        assistantMsg.setLatencyMs(latencyMs);

        List<Citation> citations = persistCitations(assistantMsg, chunks);
        assistantMsg.setCitations(citations);

        if (conversation.getTitle() == null) {
            String title = userQuery.length() > 80 ? userQuery.substring(0, 77) + "..." : userQuery;
            conversation.setTitle(title);
        }
        conversation.setUpdatedAt(OffsetDateTime.now());
        conversationRepo.save(conversation);

        analyticsService.logQuery(new QueryAnalyticsService.QueryLog(
            userQuery,
            "GENERAL",
            conversation.getLang(),
            null,
            chunks.size(),
            chunks.isEmpty() ? 0f : chunks.get(0).score(),
            latencyMs,
            chunks.isEmpty(),
            userId
        ));
    }

    // ── Conversation CRUD ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        return conversationRepo.findSummariesByUserId(userId, PageRequest.of(safePage, safeSize))
            .stream()
            .map(c -> ConversationSummary.builder()
                .id(c.getId())
                .title(c.getTitle())
                .lang(c.getLang())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .messageCount(c.getMessageCount())
                .build())
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConversationDetail getConversation(UUID id, UUID userId) {
        Conversation conv = conversationRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (!conv.getUser().getId().equals(userId)) throw new UnauthorizedException();

        return ConversationDetail.builder()
            .id(conv.getId())
            .title(conv.getTitle())
            .lang(conv.getLang())
            .createdAt(conv.getCreatedAt())
            .updatedAt(conv.getUpdatedAt())
            .messages(conv.getMessages().stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList()))
            .build();
    }

    @Transactional
    public void archiveConversation(UUID id, UUID userId) {
        Conversation conv = conversationRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (!conv.getUser().getId().equals(userId)) throw new UnauthorizedException();
        conv.setArchived(true);
    }

    @Transactional
    public void saveFeedback(UUID messageId, FeedbackRequest request, UUID userId) {
        if (request.rating() != -1 && request.rating() != 1) {
            throw new IllegalArgumentException("Rating must be -1 or 1");
        }

        Message msg = messageRepo.findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!msg.getConversation().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Message does not belong to user's conversation");
        }

        Feedback feedback = Feedback.builder()
            .message(msg)
            .userId(userId)
            .rating((short) request.rating())
            .comment(request.comment())
            .build();
        feedbackRepo.save(feedback);
    }

    // ── Helpers ───────────────────────────────────────────────

    private Conversation resolveConversation(UUID convId, User user, String lang) {
        if (convId != null) {
            Conversation c = conversationRepo.findById(convId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
            if (!c.getUser().getId().equals(user.getId())) throw new UnauthorizedException();
            return c;
        }
        Conversation c = new Conversation();
        c.setUser(user);
        c.setLang(lang != null ? lang : user.getPreferredLang());
        return conversationRepo.save(c);
    }

    private Message persistMessage(Conversation conv, String role, String content, String contentHi) {
        Message msg = new Message();
        msg.setConversation(conv);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentHi(contentHi);
        return messageRepo.save(msg);
    }

    private List<RagMessage> buildHistory(Conversation conv, UUID excludeId) {
        List<Message> recent = messageRepo.findRecentMessages(conv.getId(), excludeId, PageRequest.of(0, MAX_HISTORY_MESSAGES));
        List<Message> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);
        return chronological.stream()
            .map(m -> new RagMessage(m.getRole(), m.getContent()))
            .toList();
    }

    private List<Citation> persistCitations(Message msg, List<RagChunk> chunks) {
        if (chunks == null) return Collections.emptyList();
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk chunk = chunks.get(i);
            Citation c = Citation.builder()
                .message(msg)
                .chunkId(chunk.chunkId())
                .isNumber(chunk.isNumber())
                .clauseRef(chunk.clauseRef())
                .sectionTitle(chunk.sectionTitle())
                .excerpt(chunk.excerpt())
                .relevanceScore(chunk.score())
                .sortOrder((short) i)
                .build();
            citations.add(c);
        }
        return citations;
    }

    private MessageResponse toMessageResponse(Message msg) {
        return MessageResponse.builder()
            .id(msg.getId())
            .conversationId(msg.getConversation().getId())
            .role(msg.getRole())
            .content(msg.getContent())
            .contentHi(msg.getContentHi())
            .tokensUsed(msg.getTokensUsed())
            .latencyMs(msg.getLatencyMs())
            .citations(msg.getCitations() == null ? null :
                msg.getCitations().stream().map(c -> CitationDto.builder()
                    .chunkId(c.getChunkId())
                    .isNumber(c.getIsNumber())
                    .clauseRef(c.getClauseRef())
                    .sectionTitle(c.getSectionTitle())
                    .excerpt(c.getExcerpt())
                    .relevanceScore(c.getRelevanceScore())
                    .sortOrder(c.getSortOrder())
                    .build()).collect(Collectors.toList()))
            .createdAt(msg.getCreatedAt())
            .build();
    }
}

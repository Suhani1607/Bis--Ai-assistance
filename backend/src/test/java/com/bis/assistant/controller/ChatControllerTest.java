package com.bis.assistant.controller;

import com.bis.assistant.dto.ChatDtos.*;
import com.bis.assistant.security.JwtService;
import com.bis.assistant.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ChatService chatService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "test@bis.in", roles = "USER")
    void sendMessage_returns200WithResponse() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID msgId  = UUID.randomUUID();

        MessageResponse mockResponse = MessageResponse.builder()
            .id(msgId)
            .conversationId(convId)
            .role("assistant")
            .content("IS 2902:2006 covers hot water bottles. [IS 2902, Clause 4.1]")
            .citations(List.of(CitationDto.builder()
                .chunkId("chunk-1")
                .isNumber("IS 2902")
                .clauseRef("Clause 4.1")
                .excerpt("Hot water bottles shall conform to IS 2902.")
                .relevanceScore(0.91f)
                .sortOrder(0)
                .build()))
            .tokensUsed(180)
            .latencyMs(640)
            .createdAt(OffsetDateTime.now())
            .build();

        when(chatService.processMessage(any(), any())).thenReturn(mockResponse);

        SendMessageRequest request = new SendMessageRequest(
            "Which IS covers hot water bottles?", null, "en", false);

        mockMvc.perform(post("/chat/message")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("assistant"))
            .andExpect(jsonPath("$.citations[0].isNumber").value("IS 2902"))
            .andExpect(jsonPath("$.citations[0].clauseRef").value("Clause 4.1"))
            .andExpect(jsonPath("$.tokensUsed").value(180));
    }

    @Test
    @WithMockUser(username = "test@bis.in", roles = "USER")
    void listConversations_returns200() throws Exception {
        UUID convId = UUID.randomUUID();
        when(chatService.listConversations(any(), eq(0), eq(20)))
            .thenReturn(List.of(ConversationSummary.builder()
                .id(convId)
                .title("Which IS covers solar inverters?")
                .lang("en")
                .messageCount(2)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build()));

        mockMvc.perform(get("/chat/conversations").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Which IS covers solar inverters?"))
            .andExpect(jsonPath("$[0].messageCount").value(2));
    }

    @Test
    void sendMessage_unauthenticated_returns401() throws Exception {
        SendMessageRequest request = new SendMessageRequest(
            "Test query", null, "en", false);

        mockMvc.perform(post("/chat/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test@bis.in", roles = "USER")
    void sendMessage_blankContent_returns400() throws Exception {
        SendMessageRequest request = new SendMessageRequest("", null, "en", false);

        mockMvc.perform(post("/chat/message")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}

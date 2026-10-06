package com.bis.assistant.service;

import com.bis.assistant.dto.ChatDtos.*;
import com.bis.assistant.model.User;
import com.bis.assistant.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("bis_test")
        .withUsername("testuser")
        .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ChatService chatService;
    @Autowired UserRepository userRepo;
    @MockBean  RagClientService ragClient;

    private static UUID userId;
    private static UUID conversationId;

    @BeforeAll
    static void setup(@Autowired UserRepository userRepo) {
        User user = User.builder()
            .email("test@bis.in")
            .name("Test User")
            .passwordHash("$2a$12$dummy")
            .role(User.Role.USER)
            .preferredLang("en")
            .active(true)
            .build();
        userId = userRepo.save(user).getId();
    }

    @Test
    @Order(1)
    void sendMessage_createsConversationAndMessage() {
        // Arrange
        when(ragClient.query(any())).thenReturn(RagResponse.builder()
            .answer("IS 14286:1995 covers solar photovoltaic systems.")
            .chunks(List.of(RagChunk.builder()
                .chunkId("chunk-001")
                .isNumber("IS 14286")
                .clauseRef("Clause 3.1")
                .sectionTitle("Scope")
                .excerpt("This standard applies to solar PV systems.")
                .score(0.92f)
                .build()))
            .detectedIntent("FIND_STANDARD")
            .detectedLang("en")
            .tokensUsed(250)
            .latencyMs(800)
            .build());

        // Act
        SendMessageRequest req = new SendMessageRequest(
            "Which IS covers solar inverters?", null, "en", false);
        MessageResponse response = chatService.processMessage(req, userId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.content()).contains("IS 14286");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).isNumber()).isEqualTo("IS 14286");
        assertThat(response.conversationId()).isNotNull();

        conversationId = response.conversationId();
    }

    @Test
    @Order(2)
    void listConversations_returnsCreatedConversation() {
        List<ConversationSummary> list = chatService.listConversations(userId, 0, 10);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(conversationId);
        assertThat(list.get(0).title()).contains("solar inverters");
    }

    @Test
    @Order(3)
    void getConversation_returnsMessagesWithCitations() {
        ConversationDetail detail = chatService.getConversation(conversationId, userId);
        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().get(0).role()).isEqualTo("user");
        assertThat(detail.messages().get(1).role()).isEqualTo("assistant");
        assertThat(detail.messages().get(1).citations()).isNotEmpty();
    }

    @Test
    @Order(4)
    void archiveConversation_removesFromList() {
        chatService.archiveConversation(conversationId, userId);
        List<ConversationSummary> list = chatService.listConversations(userId, 0, 10);
        assertThat(list).isEmpty();
    }

    @Test
    void getConversation_wrongUser_throwsUnauthorized() {
        UUID otherId = UUID.randomUUID();
        assertThatThrownBy(() -> chatService.getConversation(conversationId, otherId))
            .isInstanceOf(RuntimeException.class);
    }
}

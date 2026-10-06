package com.bis.assistant.repository;

import com.bis.assistant.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    @Query("""
        SELECT m FROM Message m
        WHERE m.conversation.id = :conversationId
          AND (:excludeId IS NULL OR m.id != :excludeId)
        ORDER BY m.createdAt DESC
    """)
    List<Message> findRecentMessages(
        @Param("conversationId") UUID conversationId,
        @Param("excludeId") UUID excludeId,
        Pageable pageable);
}

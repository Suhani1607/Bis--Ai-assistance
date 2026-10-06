package com.bis.assistant.repository;

import com.bis.assistant.model.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByUserIdAndArchivedFalseOrderByUpdatedAtDesc(
        UUID userId, Pageable pageable);

    @Query("""
        SELECT c.id AS id, c.title AS title, c.lang AS lang,
               c.createdAt AS createdAt, c.updatedAt AS updatedAt,
               COUNT(m) AS messageCount
        FROM Conversation c
        LEFT JOIN c.messages m
        WHERE c.user.id = :userId AND c.archived = false
        GROUP BY c.id, c.title, c.lang, c.createdAt, c.updatedAt
        ORDER BY c.updatedAt DESC
    """)
    List<ConversationSummaryView> findSummariesByUserId(@Param("userId") UUID userId, Pageable pageable);

    interface ConversationSummaryView {
        UUID getId();
        String getTitle();
        String getLang();
        OffsetDateTime getCreatedAt();
        OffsetDateTime getUpdatedAt();
        int getMessageCount();
    }
}

package com.bis.assistant.repository;

import com.bis.assistant.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    @Query("SELECT AVG(f.rating) FROM Feedback f " +
           "WHERE f.message.conversation.user.id = :userId")
    Double avgRatingForUser(@Param("userId") UUID userId);
}

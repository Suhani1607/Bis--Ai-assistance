package com.bis.assistant.repository;

import com.bis.assistant.model.IsCatalogue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IsCatalogueRepository extends JpaRepository<IsCatalogue, UUID> {

    Optional<IsCatalogue> findByIsNumber(String isNumber);

    /**
     * Trigram similarity search over title + ILIKE fallback on is_number.
     * Uses pg_trgm extension (enabled in V1 migration).
     */
    @Query(value = """
        SELECT * FROM is_catalogue
        WHERE status = 'CURRENT'
          AND (
            is_number ILIKE :q
            OR title   ILIKE :q
            OR similarity(title, :raw) > 0.15
          )
        ORDER BY similarity(title, :raw) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<IsCatalogue> searchByTrigram(
        @Param("q")     String q,
        @Param("raw")   String raw,
        @Param("limit") int limit);
}

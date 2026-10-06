package com.bis.assistant.repository;

import com.bis.assistant.model.IngestionJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    @Query("SELECT j FROM IngestionJob j ORDER BY j.createdAt DESC")
    List<IngestionJob> findRecentJobs(Pageable pageable);
}

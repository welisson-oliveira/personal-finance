package com.personalfinance.repository;

import com.personalfinance.model.entity.ImportSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportSessionRepository extends JpaRepository<ImportSession, UUID> {

  List<ImportSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

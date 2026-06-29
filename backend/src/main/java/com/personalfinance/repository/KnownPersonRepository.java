package com.personalfinance.repository;

import com.personalfinance.model.entity.KnownPerson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnownPersonRepository extends JpaRepository<KnownPerson, UUID> {

  List<KnownPerson> findByUserIdAndActiveTrue(UUID userId);

  Optional<KnownPerson> findByIdAndUserId(UUID id, UUID userId);
}

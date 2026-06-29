package com.personalfinance.repository;

import com.personalfinance.model.entity.MerchantAlias;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantAliasRepository extends JpaRepository<MerchantAlias, UUID> {

  @Query("SELECT a FROM MerchantAlias a JOIN FETCH a.merchantRule r WHERE r.user IS NULL")
  List<MerchantAlias> findAllGlobal();
}

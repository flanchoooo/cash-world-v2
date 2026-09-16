package com.poscloud.wallet.transaction;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<TransactionRecord> findByUserIdAndIdempotencyKey(UUID userId, String key);

  Optional<TransactionRecord> findByTransactionReference(String ref);

  boolean existsByOriginalTransactionReference(String ref);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<TransactionRecord> findByOriginalTransactionReference(String ref);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from TransactionRecord t where t.transactionReference=:ref")
  Optional<TransactionRecord> lock(@Param("ref") String ref);
}

package com.poscloud.wallet.remittance;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RemittanceRepository extends JpaRepository<Remittance, UUID> {
  Optional<Remittance> findByRemittanceReference(String ref);

  Optional<Remittance> findByTransactionReference(String ref);

  Optional<Remittance> findByPayoutTransactionReference(String ref);

  Optional<Remittance> findByReceiptShareToken(String token);

  List<Remittance> findAllByCollectionCode(String collectionCode);

  @Query(
      value =
          "select * from remittances where regexp_replace(sender_mobile,'[^0-9]','')=:mobile order by created_at desc limit 1",
      nativeQuery = true)
  Optional<Remittance> findLatestSenderByNormalizedMobile(@Param("mobile") String mobile);

  @Query(
      value =
          "select * from remittances where regexp_replace(receiver_mobile,'[^0-9]','')=:mobile order by created_at desc limit 1",
      nativeQuery = true)
  Optional<Remittance> findLatestRecipientByNormalizedMobile(@Param("mobile") String mobile);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Remittance r where r.remittanceReference=:ref")
  Optional<Remittance> lock(@Param("ref") String ref);
}

package com.poscloud.wallet.wallet;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
  Optional<Wallet> findByWalletNumber(String number);

  List<Wallet> findByCustomerId(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Wallet w where w.id=:id")
  Optional<Wallet> lock(@Param("id") UUID id);
}

package com.poscloud.wallet.transaction;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, UUID> {
  Optional<TransactionType> findByCode(String code);
}

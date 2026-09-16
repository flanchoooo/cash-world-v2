package com.poscloud.wallet.fee;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FeeRepository extends JpaRepository<Fee, UUID> {
  List<Fee> findByTransactionTypeIdAndCurrencyId(UUID type, UUID currency);
}

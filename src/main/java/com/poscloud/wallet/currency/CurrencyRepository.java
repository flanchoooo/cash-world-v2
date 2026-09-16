package com.poscloud.wallet.currency;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
  Optional<Currency> findByCode(String code);
}

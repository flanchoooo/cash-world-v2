package com.poscloud.wallet.biller;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BillerProductRepository extends JpaRepository<BillerProduct, UUID> {
  Optional<BillerProduct> findByCode(String code);
  List<BillerProduct> findByNameIgnoreCaseAndCurrencyId(String name, UUID currencyId);
}

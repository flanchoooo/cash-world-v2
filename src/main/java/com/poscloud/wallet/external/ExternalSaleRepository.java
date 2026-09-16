package com.poscloud.wallet.external;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalSaleRepository extends JpaRepository<ExternalSale, UUID> {
  Optional<ExternalSale> findByTransactionReference(String reference);
  List<ExternalSale> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
  List<ExternalSale> findByCreditSaleTrueOrderByCreatedAtDesc();
}

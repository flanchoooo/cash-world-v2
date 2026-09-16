package com.poscloud.wallet.biller;

import com.poscloud.wallet.common.Types.Status;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProductAllocationRepository
    extends JpaRepository<CustomerProductAllocation, UUID> {
  Optional<CustomerProductAllocation> findByCustomerIdAndBillerProductId(UUID customer, UUID product);

  List<CustomerProductAllocation> findByCustomerIdAndStatusOrderByCreatedAt(UUID customer, Status status);

  List<CustomerProductAllocation> findByCustomerIdOrderByCreatedAtDesc(UUID customer);
}

package com.poscloud.wallet.biller;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCommissionPlanRepository
    extends JpaRepository<ProductCommissionPlan, UUID> {
  boolean existsByBillerProductIdAndStatus(UUID billerProductId, com.poscloud.wallet.common.Types.Status status);
  Optional<ProductCommissionPlan> findFirstByBillerProductIdAndStatusOrderByCreatedAtDesc(
      UUID billerProductId, com.poscloud.wallet.common.Types.Status status);
}

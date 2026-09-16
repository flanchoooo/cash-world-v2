package com.poscloud.wallet.biller;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCommissionPlanRepository
    extends JpaRepository<ProductCommissionPlan, UUID> {}

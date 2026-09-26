package com.poscloud.wallet.biller;

import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillerService {
  private final BillerRepository billers;
  private final BillerProductRepository products;
  private final ProductCommissionPlanRepository commissionPlans;
  private final com.poscloud.wallet.wallet.WalletService wallets;

  public BillerProduct product(String code) {
    var p = products.findByCode(code).orElseThrow(() -> new ApiException("PRODUCT_NOT_FOUND"));
    ApiException.require(p.getStatus() == Status.ACTIVE, "PRODUCT_NOT_FOUND");
    biller(p);
    wallets.currency(p.getCurrencyId());
    ApiException.require(
        commissionPlans.existsByBillerProductIdAndStatus(p.getId(), Status.ACTIVE),
        "PRODUCT_PLAN_NOT_FOUND");
    return p;
  }

  public Biller biller(BillerProduct p) {
    var b =
        billers.findById(p.getBillerId()).orElseThrow(() -> new ApiException("BILLER_NOT_FOUND"));
    ApiException.require(b.getStatus() == Status.ACTIVE, "BILLER_NOT_FOUND");
    return b;
  }
}

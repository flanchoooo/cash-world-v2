package com.poscloud.wallet.biller;

import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.wallet.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillerService {
  private final BillerRepository billers;
  private final BillerProductRepository products;
  private final WalletRepository wallets;

  public BillerProduct product(String code) {
    var p = products.findByCode(code).orElseThrow(() -> new ApiException("PRODUCT_NOT_FOUND"));
    ApiException.require(p.getStatus() == Status.ACTIVE, "PRODUCT_NOT_FOUND");
    biller(p);
    var w =
        wallets
            .findById(p.getSettlementWalletId())
            .orElseThrow(() -> new ApiException("WALLET_NOT_FOUND"));
    ApiException.require(
        w.getCurrencyId().equals(p.getCurrencyId()) && w.getWalletType() == WalletType.BILLER,
        "INVALID_SETTLEMENT_WALLET");
    return p;
  }

  public Biller biller(BillerProduct p) {
    var b =
        billers.findById(p.getBillerId()).orElseThrow(() -> new ApiException("BILLER_NOT_FOUND"));
    ApiException.require(b.getStatus() == Status.ACTIVE, "BILLER_NOT_FOUND");
    return b;
  }
}

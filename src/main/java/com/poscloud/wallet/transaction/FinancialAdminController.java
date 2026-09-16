package com.poscloud.wallet.transaction;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FinancialAdminController {
  private final ReversalService reversals;
  private final AdjustmentService adjustments;

  @PostMapping("/transactions/{ref}/reverse")
  public TransactionResult reverse(
      @PathVariable String ref,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ReversalService.Request r) {
    return reversals.reverse(ref, key, r);
  }

  @PostMapping("/admin/wallets/adjust")
  public TransactionResult adjust(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody AdjustmentService.Request r) {
    return adjustments.adjust(key, r);
  }
}

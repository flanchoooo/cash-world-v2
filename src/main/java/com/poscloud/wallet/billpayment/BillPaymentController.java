package com.poscloud.wallet.billpayment;

import com.poscloud.wallet.transaction.TransactionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bill-payments")
@RequiredArgsConstructor
public class BillPaymentController {
  private final BillPaymentService service;

  @PostMapping
  public TransactionResult pay(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody BillPaymentService.Request r) {
    return service.pay(key, r);
  }

  @PostMapping("/{ref}/enquire")
  public TransactionResult enquire(@PathVariable String ref) {
    return service.enquire(ref);
  }
}

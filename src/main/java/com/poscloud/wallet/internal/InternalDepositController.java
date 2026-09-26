package com.poscloud.wallet.internal;

import com.poscloud.wallet.transaction.TransactionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/wallets/deposit")
@RequiredArgsConstructor
public class InternalDepositController {
  private final InternalDepositService service;

  @PostMapping("/validate")
  public InternalDepositService.Target validate(@Valid @RequestBody InternalDepositService.TargetRequest request) {
    return service.validate(request);
  }

  @PostMapping
  public TransactionResult deposit(@Valid @RequestBody InternalDepositService.DepositRequest request) {
    return service.deposit(request);
  }
}

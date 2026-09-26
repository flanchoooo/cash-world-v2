package com.poscloud.wallet.internal;

import com.poscloud.wallet.transaction.TransactionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/internal/wallets")
@RequiredArgsConstructor
public class InternalWalletController {
  private final InternalWalletService service;

  @PostMapping("/balance")
  public List<InternalWalletService.Balance> balance(@Valid @RequestBody InternalWalletService.BalanceRequest request) {
    return service.balance(request);
  }

  @PostMapping("/verify-pin")
  public InternalWalletService.PinVerificationResult verifyPin(
      @Valid @RequestBody InternalWalletService.PinVerificationRequest request) {
    return service.verifyPin(request);
  }

  @PostMapping("/debit")
  public TransactionResult debit(@Valid @RequestBody InternalWalletService.MutationRequest request) {
    return service.debit(request);
  }

  @PostMapping("/credit")
  public TransactionResult credit(@Valid @RequestBody InternalWalletService.MutationRequest request) {
    return service.credit(request);
  }

  @PostMapping("/transactions/{reference}/reverse")
  public TransactionResult reverse(@PathVariable String reference,
                                   @Valid @RequestBody InternalWalletService.ReversalRequest request) {
    return service.reverse(reference, request);
  }

  @PostMapping("/transactions/{reference}/provider-result")
  public void providerResult(@PathVariable String reference,
      @Valid @RequestBody InternalWalletService.ProviderResult request) {
    service.recordProviderResult(reference, request);
  }
}

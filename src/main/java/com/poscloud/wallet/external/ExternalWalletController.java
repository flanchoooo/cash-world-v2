package com.poscloud.wallet.external;

import com.poscloud.wallet.auth.AuthService;
import com.poscloud.wallet.biller.CustomerProductAllocationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
public class ExternalWalletController {
  private final ExternalWalletService service;
  private final AuthService auth;

  @PostMapping("/login")
  public AuthService.Tokens login(@Valid @RequestBody AuthService.Login request) {
    return auth.externalLogin(request);
  }

  @GetMapping("/balances")
  public List<ExternalWalletService.Balance> balances() { return service.balances(); }

  @GetMapping("/products")
  public List<CustomerProductAllocationService.View> products() { return service.products(); }

  @PostMapping("/sales")
  public ExternalWalletService.SaleView sale(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ExternalWalletService.SaleRequest request) {
    return service.sale(key, request);
  }

  @GetMapping("/transactions")
  public List<ExternalWalletService.SaleView> history(
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "50") int limit) {
    return service.history(offset, limit);
  }

  @GetMapping("/transactions/{reference}")
  public ExternalWalletService.SaleView transaction(@PathVariable String reference) {
    return service.get(reference);
  }

  @PostMapping("/transactions/{reference}/reverse")
  public ExternalWalletService.SaleView reverse(
      @PathVariable String reference,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ExternalWalletService.ReversalRequest request) {
    return service.reverse(reference, key, request);
  }
}

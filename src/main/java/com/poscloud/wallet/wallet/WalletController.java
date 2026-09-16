package com.poscloud.wallet.wallet;

import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.transaction.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {
  private final WalletService wallets;
  private final TransferService transfers;
  private final TransactionQueryService queries;

  @PostMapping
  public WalletService.View create(@Valid @RequestBody WalletService.Create r) {
    return wallets.create(r);
  }

  @GetMapping("/{number}")
  public WalletService.View get(@PathVariable String number) {
    return wallets.get(number);
  }

  @PostMapping("/{number}/block")
  public WalletService.View block(@PathVariable String number) {
    return wallets.status(number, WalletStatus.BLOCKED);
  }

  @PostMapping("/{number}/activate")
  public WalletService.View activate(@PathVariable String number) {
    return wallets.status(number, WalletStatus.ACTIVE);
  }

  @PostMapping("/deposit")
  public TransactionResult deposit(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody TransferService.Cash r) {
    return transfers.deposit(key, r);
  }

  @PostMapping("/withdraw")
  public TransactionResult withdraw(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody TransferService.Cash r) {
    return transfers.withdraw(key, r);
  }

  @PostMapping("/send-money")
  public TransactionResult send(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody TransferService.Send r) {
    return transfers.send(key, r);
  }

  @GetMapping("/{number}/transactions")
  public List<TransactionResult> history(
      @PathVariable String number,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "50") int limit) {
    return queries.walletHistory(number, offset, limit);
  }

  @GetMapping("/{number}/statement")
  public List<TransactionQueryService.StatementLine> statement(
      @PathVariable String number,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "100") int limit) {
    return queries.statement(number, offset, limit);
  }
}

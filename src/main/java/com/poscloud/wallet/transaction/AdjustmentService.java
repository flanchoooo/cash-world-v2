package com.poscloud.wallet.transaction;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.wallet.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdjustmentService {
  private final IdempotencyService idempotency;
  private final WalletService wallets;
  private final LedgerService ledger;
  private final TransactionQueryService queries;
  private final AccessService access;
  private final AuditService audit;

  @io.swagger.v3.oas.annotations.media.Schema(name = "AdjustmentServiceRequest")
  public record Request(
      @NotBlank String wallet,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
          BigDecimal amount,
      @NotNull Direction direction,
      @NotBlank @Size(max = 500) String reason) {}

  public TransactionResult adjust(String key, Request r) {
    access.requireStaff();
    access.requirePermission(Permission.WALLET_ADJUST);
    return idempotency.execute(
        key,
        "ACCOUNT_ADJUSTMENT",
        r,
        t -> {
          var w = wallets.find(r.wallet());
          var system = wallets.system("SUSPENSE", w.getCurrencyId());
          var debit = r.direction() == Direction.CREDIT ? system : w;
          var credit = r.direction() == Direction.CREDIT ? w : system;
          ledger.postEntry(
              t.getTransactionReference(),
              "ACCOUNT_ADJUSTMENT",
              debit.getId(),
              credit.getId(),
              r.amount(),
              w.getCurrencyId(),
              EntryType.ADJUSTMENT,
              LedgerService.Metadata.basic(w.getCustomerId(), r.amount(), r.reason()));
          audit.record("ACCOUNT_ADJUSTMENT", "wallets", w.getId(), null, idempotency.serialize(r));
          return queries.result(t.getTransactionReference(), null);
        });
  }
}

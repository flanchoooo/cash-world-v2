package com.poscloud.wallet.internal;

import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.ApiException;
import com.poscloud.wallet.common.Money;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerRepository;
import com.poscloud.wallet.currency.CurrencyRepository;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service-account deposit contract for externally collected agent or customer top-ups. */
@Service
@RequiredArgsConstructor
public class InternalDepositService {
  private final AccessService access;
  private final CustomerRepository customers;
  private final CurrencyRepository currencies;
  private final WalletTypeRepository walletTypes;
  private final WalletRepository repository;
  private final WalletService wallets;
  private final LedgerService ledger;
  private final IdempotencyService idempotency;
  private final TransactionQueryService queries;

  public record TargetRequest(@NotBlank String customerNumber, @NotBlank String currency,
      @NotBlank String walletType,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount) {}

  public record Target(String walletNumber, UUID walletId, String customerNumber, String currency,
      String walletType, BigDecimal amount) {}

  public record DepositRequest(@NotBlank @Size(max = 120) String requestId,
      @NotBlank String customerNumber, @NotBlank String currency, @NotBlank String walletType,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
      @NotBlank String walletNumber, @NotNull UUID walletId,
      @NotBlank @Pattern(regexp = "ECOCASH|OMARI") String paymentMethod,
      @NotBlank @Size(max = 200) String providerReference) {
    TargetRequest target() { return new TargetRequest(customerNumber, currency, walletType, amount); }
  }

  @Transactional(readOnly = true)
  public Target validate(TargetRequest request) {
    requireService();
    var wallet = resolve(request);
    var settlement = wallets.system("CASH_SETTLEMENT", wallet.getCurrencyId());
    wallets.usable(settlement);
    // Validate settlement configuration before asking a customer to authorize collection.
    var amount = Money.amount(request.amount(), wallets.currency(wallet.getCurrencyId()).getDecimalPlaces());
    ApiException.require(settlement.isAllowNegativeBalance() || settlement.getBalance().compareTo(amount) >= 0,
        "INSUFFICIENT_SETTLEMENT_FUNDS");
    ApiException.require(wallet.getBalance().add(amount).compareTo(Money.MAX) <= 0, "BALANCE_LIMIT_EXCEEDED");
    return new Target(wallet.getWalletNumber(), wallet.getId(), request.customerNumber().trim(),
        request.currency().trim().toUpperCase(Locale.ROOT), request.walletType().trim().toUpperCase(Locale.ROOT), amount);
  }

  public TransactionResult deposit(DepositRequest request) {
    requireService();
    return idempotency.execute(request.requestId(), "ESB_WALLET_DEPOSIT", request, transaction -> {
      var wallet = resolve(request.target());
      ApiException.require(wallet.getId().equals(request.walletId())
          && wallet.getWalletNumber().equals(request.walletNumber()), "DEPOSIT_WALLET_MISMATCH");
      var settlement = wallets.system("CASH_SETTLEMENT", wallet.getCurrencyId());
      var customer = customers.findByCustomerNumber(request.customerNumber().trim()).orElseThrow();
      var metadata = new LedgerService.Metadata(wallet.getCustomerId(),
          customer.isAgent() ? wallet.getCustomerId() : null, null, null,
          request.amount(), BigDecimal.ZERO, BigDecimal.ZERO, RewardMode.NONE, request.providerReference(),
          null, request.paymentMethod() + (customer.isAgent() ? " agent wallet top-up" : " customer wallet top-up"),
          request.requestId());
      // Deposit the full collected principal. No bill-payment discounts, fees or commissions apply.
      ledger.postEntry(transaction.getTransactionReference(), "DEPOSIT", settlement.getId(), wallet.getId(),
          request.amount(), wallet.getCurrencyId(), EntryType.PRINCIPAL, metadata);
      return queries.result(transaction.getTransactionReference(), null);
    });
  }

  private Wallet resolve(TargetRequest request) {
    var customer = customers.findByCustomerNumber(request.customerNumber().trim())
        .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
    ApiException.require(customer.getStatus() == CustomerStatus.ACTIVE, "CUSTOMER_BLOCKED");
    var currency = currencies.findByCode(request.currency().trim().toUpperCase(Locale.ROOT))
        .filter(c -> c.getStatus() == Status.ACTIVE).orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
    Money.amount(request.amount(), currency.getDecimalPlaces());
    var type = walletTypes.findByCode(request.walletType().trim().toUpperCase(Locale.ROOT))
        .filter(t -> t.getStatus() == Status.ACTIVE && t.getScope() == WalletScope.CUSTOMER)
        .orElseThrow(() -> new ApiException("INVALID_WALLET_TYPE"));
    var matches = repository.findByCustomerIdAndWalletTypeIdAndCurrencyId(customer.getId(), type.getId(), currency.getId());
    ApiException.require(!matches.isEmpty(), "WALLET_NOT_FOUND");
    ApiException.require(matches.size() == 1, "WALLET_SELECTION_REQUIRED");
    var wallet = matches.get(0);
    ApiException.require(wallet.getWalletType() == WalletType.CUSTOMER, "INVALID_WALLET_TYPE");
    wallets.usable(wallet);
    return wallet;
  }

  private void requireService() {
    ApiException.require(access.current().getRole() == Role.ESB_SERVICE, "FORBIDDEN");
  }
}

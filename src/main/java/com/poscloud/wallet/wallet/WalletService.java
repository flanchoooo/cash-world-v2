package com.poscloud.wallet.wallet;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.currency.CurrencyRepository;
import com.poscloud.wallet.customer.CustomerRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {
  private final WalletRepository wallets;
  private final CurrencyRepository currencies;
  private final CustomerRepository customers;
  private final WalletTypeRepository walletTypes;
  private final AccessService access;
  private final AuditService audit;

  @io.swagger.v3.oas.annotations.media.Schema(name = "WalletServiceCreate")
  public record Create(
      UUID customerId,
      UUID walletTypeId,
      UUID currencyId,
      String currency,
      @NotBlank @Size(max = 200) String name) {
    public Create(UUID customerId, String currency, String name) {
      this(customerId, null, null, currency, name);
    }
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "WalletServiceView")
  public record View(
      UUID id,
      String walletNumber,
      UUID customerId,
      UUID walletTypeId,
      String currency,
      String walletType,
      String name,
      BigDecimal balance,
      boolean allowNegativeBalance,
      WalletStatus status) {}

  public Wallet find(String number) {
    return wallets
        .findByWalletNumber(number)
        .orElseThrow(() -> new ApiException("WALLET_NOT_FOUND"));
  }

  public com.poscloud.wallet.currency.Currency currency(UUID id) {
    var c = currencies.findById(id).orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
    ApiException.require(c.getStatus() == Status.ACTIVE, "INVALID_CURRENCY");
    return c;
  }

  public WalletTypeDefinition walletType(UUID id) {
    return walletTypes.findById(id).orElseThrow(() -> new ApiException("INVALID_WALLET_TYPE"));
  }

  public Wallet system(String prefix, UUID currency) {
    return find(prefix + "_" + currency(currency).getCode());
  }

  public void owned(Wallet w) {
    access.customer(w.getCustomerId());
  }

  public void usable(Wallet w) {
    ApiException.require(w.getStatus() == WalletStatus.ACTIVE, "WALLET_BLOCKED");
    currency(w.getCurrencyId());
    if (w.getCustomerId() != null) {
      var c =
          customers
              .findById(w.getCustomerId())
              .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
      ApiException.require(c.getStatus() == CustomerStatus.ACTIVE, "CUSTOMER_BLOCKED");
    }
  }

  @Transactional
  public View create(Create r) {
    access.customer(r.customerId());
    ApiException.require(r.customerId() != null, "CUSTOMER_REQUIRED");
    ApiException.require(r.currencyId() != null || (r.currency() != null && !r.currency().isBlank()), "CURRENCY_REQUIRED");
    var c =
        customers
            .findById(r.customerId())
            .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
    ApiException.require(c.getStatus() == CustomerStatus.ACTIVE, "CUSTOMER_BLOCKED");
    var currency = r.currencyId() != null
        ? currencies.findById(r.currencyId()).orElseThrow(() -> new ApiException("INVALID_CURRENCY"))
        : currencies.findByCode(r.currency()).orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
    ApiException.require(currency.getStatus() == Status.ACTIVE, "INVALID_CURRENCY");
    var type = r.walletTypeId() == null
        ? walletTypes.findByCode("CUSTOMER").orElseThrow(() -> new ApiException("INVALID_WALLET_TYPE"))
        : walletTypes.findById(r.walletTypeId()).orElseThrow(() -> new ApiException("INVALID_WALLET_TYPE"));
    ApiException.require(type.getStatus() == Status.ACTIVE && type.getScope() == WalletScope.CUSTOMER, "INVALID_WALLET_TYPE");
    ApiException.require(wallets.findByCustomerIdAndWalletTypeIdAndCurrencyId(c.getId(), type.getId(), currency.getId()).isEmpty(), "WALLET_EXISTS");
    var w = new Wallet();
    w.setWalletNumber("WAL" + UUID.randomUUID().toString().replace("-", ""));
    w.setCustomerId(c.getId());
    w.setCurrencyId(currency.getId());
    w.setWalletTypeId(type.getId());
    w.setWalletType(WalletType.CUSTOMER);
    w.setName(r.name());
    w.setStatus(WalletStatus.ACTIVE);
    wallets.save(w);
    audit.record("WALLET_CREATED", "wallets", w.getId());
    return view(w);
  }

  public View view(Wallet w) {
    return new View(
        w.getId(),
        w.getWalletNumber(),
        w.getCustomerId(),
        w.getWalletTypeId(),
        currencies.findById(w.getCurrencyId()).orElseThrow().getCode(),
        walletTypes.findById(w.getWalletTypeId()).orElseThrow().getCode(),
        w.getName(),
        w.getBalance(),
        w.isAllowNegativeBalance(),
        w.getStatus());
  }

  @Transactional(readOnly = true)
  public View get(String number) {
    var w = find(number);
    owned(w);
    return view(w);
  }

  @Transactional
  public View status(String number, WalletStatus status) {
    access.requireStaff();
    var found = find(number);
    var w = wallets.lock(found.getId()).orElseThrow();
    w.setStatus(status);
    audit.record("WALLET_" + status, "wallets", w.getId());
    return view(w);
  }
}

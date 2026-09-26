package com.poscloud.wallet.wallet;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.transaction.IdempotencyService;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemWalletService {
  private final WalletRepository repository;
  private final AccessService access;
  private final AuditService audit;
  private final EntityManager entityManager;
  private final IdempotencyService json;
  private final WalletTypeRepository walletTypes;

  @io.swagger.v3.oas.annotations.media.Schema(name = "SystemWalletServiceRequest")
  public record Request(
      @NotBlank @Size(max = 200) String walletNumber,
      @NotNull UUID currencyId,
      @NotNull WalletType walletType,
      @NotBlank @Size(max = 200) String name,
      boolean allowNegativeBalance,
      @NotNull WalletStatus status) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "SystemWalletServiceView")
  public record View(
      UUID id,
      String walletNumber,
      UUID currencyId,
      WalletType walletType,
      String name,
      boolean allowNegativeBalance,
      WalletStatus status) {}

  private View view(Wallet e) {
    return new View(
        e.getId(),
        e.getWalletNumber(),
        e.getCurrencyId(),
        e.getWalletType(),
        e.getName(),
        e.isAllowNegativeBalance(),
        e.getStatus());
  }

  public View create(Request r) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    validate(r, null);
    var e = new Wallet();
    apply(e, r);
    repository.save(e);
    audit.record("WALLET_CREATED", "system-wallets", e.getId(), null, json.serialize(view(e)));
    return view(e);
  }

  public View update(UUID id, Request r) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    var e = repository.lock(id).orElseThrow(() -> new ApiException("WALLET_NOT_FOUND"));
    validate(r, e);
    var before = json.serialize(view(e));
    apply(e, r);
    audit.record("WALLET_UPDATED", "system-wallets", id, before, json.serialize(view(e)));
    return view(e);
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    access.requireStaff();
    access.requirePermission(Permission.CONFIGURATION_VIEW);
    return repository.findAll().stream()
        .filter(e -> e.getCustomerId() == null)
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    access.requireStaff();
    access.requirePermission(Permission.CONFIGURATION_VIEW);
    var e = repository.findById(id).orElseThrow(() -> new ApiException("WALLET_NOT_FOUND"));
    ApiException.require(e.getCustomerId() == null, "INVALID_WALLET_TYPE");
    return view(e);
  }

  public View status(UUID id, boolean active) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    var e = repository.lock(id).orElseThrow(() -> new ApiException("WALLET_NOT_FOUND"));
    ApiException.require(e.getCustomerId() == null, "INVALID_WALLET_TYPE");
    var before = json.serialize(view(e));
    e.setStatus(active ? WalletStatus.ACTIVE : WalletStatus.BLOCKED);
    audit.record("WALLET_STATUS_CHANGED", "system-wallets", id, before, json.serialize(view(e)));
    return view(e);
  }

  private void apply(Wallet e, Request r) {
    e.setWalletNumber(r.walletNumber());
    e.setCurrencyId(r.currencyId());
    e.setWalletTypeId(walletTypes.findByCode(r.walletType().name()).orElseThrow(() -> new ApiException("INVALID_WALLET_TYPE")).getId());
    e.setWalletType(r.walletType());
    e.setName(r.name());
    e.setAllowNegativeBalance(r.allowNegativeBalance());
    e.setStatus(r.status());
  }

  private void validate(Request r, Wallet existing) {
    ApiException.require(
        r.walletType() == WalletType.SYSTEM || r.walletType() == WalletType.BILLER,
        "INVALID_WALLET_TYPE");
    ApiException.require(
        entityManager.find(com.poscloud.wallet.currency.Currency.class, r.currencyId()) != null,
        "INVALID_CURRENCY");
    if (existing != null) {
      ApiException.require(existing.getCustomerId() == null, "INVALID_WALLET_TYPE");
      ApiException.require(
          existing.getCurrencyId().equals(r.currencyId())
              && existing.getWalletNumber().equals(r.walletNumber())
              && existing.getWalletType() == r.walletType(),
          "WALLET_DEFINITION_IMMUTABLE");
      ApiException.require(
          r.allowNegativeBalance() || existing.getBalance().signum() >= 0, "INSUFFICIENT_FUNDS");
    }
  }

  private void range(BigDecimal min, BigDecimal max) {
    ApiException.require(min == null || max == null || min.compareTo(max) <= 0, "INVALID_RANGE");
  }
}

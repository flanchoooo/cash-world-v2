package com.poscloud.wallet.biller;

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
public class BillerProductService {
  private final BillerProductRepository repository;
  private final AccessService access;
  private final AuditService audit;
  private final EntityManager entityManager;
  private final IdempotencyService json;

  @io.swagger.v3.oas.annotations.media.Schema(name = "BillerProductServiceRequest")
  public record Request(
      @NotNull UUID billerId,
      @NotBlank @Size(max = 200) String code,
      @NotBlank @Size(max = 200) String name,
      @NotNull UUID currencyId,
      UUID walletTypeId,
      @NotNull UUID settlementWalletId,
      @NotNull RewardMode agentRewardMode,
      @NotNull RewardType agentRewardType,
      @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal agentRewardValue,
      @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal minimumCommission,
      @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal maximumCommission,
      @NotNull Status status) {
    private static final UUID DEFAULT_WALLET_TYPE = UUID.fromString("11000000-0000-0000-0000-000000000001");

    public Request(
        UUID billerId, String code, String name, UUID currencyId, UUID settlementWalletId,
        RewardMode agentRewardMode, RewardType agentRewardType, BigDecimal agentRewardValue,
        BigDecimal minimumCommission, BigDecimal maximumCommission, Status status) {
      this(billerId, code, name, currencyId, DEFAULT_WALLET_TYPE, settlementWalletId,
          agentRewardMode, agentRewardType, agentRewardValue, minimumCommission, maximumCommission, status);
    }

    @Override public UUID walletTypeId() { return walletTypeId == null ? DEFAULT_WALLET_TYPE : walletTypeId; }
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "BillerProductServiceView")
  public record View(
      UUID id,
      UUID billerId,
      String code,
      String name,
      UUID currencyId,
      UUID walletTypeId,
      UUID settlementWalletId,
      RewardMode agentRewardMode,
      RewardType agentRewardType,
      BigDecimal agentRewardValue,
      BigDecimal minimumCommission,
      BigDecimal maximumCommission,
      Status status) {}

  private View view(BillerProduct e) {
    return new View(
        e.getId(),
        e.getBillerId(),
        e.getCode(),
        e.getName(),
        e.getCurrencyId(),
        e.getWalletTypeId(),
        e.getSettlementWalletId(),
        e.getAgentRewardMode(),
        e.getAgentRewardType(),
        e.getAgentRewardValue(),
        e.getMinimumCommission(),
        e.getMaximumCommission(),
        e.getStatus());
  }

  public View create(Request r) {
    access.requireStaff();
    validate(r, null);
    var e = new BillerProduct();
    apply(e, r);
    repository.save(e);
    audit.record(
        "BILLERPRODUCT_CREATED", "biller-products", e.getId(), null, json.serialize(view(e)));
    return view(e);
  }

  public View update(UUID id, Request r) {
    access.requireStaff();
    var e = repository.findById(id).orElseThrow(() -> new ApiException("BILLERPRODUCT_NOT_FOUND"));
    validate(r, e);
    var before = json.serialize(view(e));
    apply(e, r);
    audit.record("BILLERPRODUCT_UPDATED", "biller-products", id, before, json.serialize(view(e)));
    return view(e);
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    access.requireStaff();
    return repository.findAll().stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    access.requireStaff();
    var e = repository.findById(id).orElseThrow(() -> new ApiException("BILLERPRODUCT_NOT_FOUND"));
    return view(e);
  }

  public View status(UUID id, boolean active) {
    access.requireStaff();
    var e = repository.findById(id).orElseThrow(() -> new ApiException("BILLERPRODUCT_NOT_FOUND"));
    var before = json.serialize(view(e));
    e.setStatus(active ? Status.ACTIVE : Status.INACTIVE);
    audit.record(
        "BILLERPRODUCT_STATUS_CHANGED", "biller-products", id, before, json.serialize(view(e)));
    return view(e);
  }

  private void apply(BillerProduct e, Request r) {
    e.setBillerId(r.billerId());
    e.setCode(r.code());
    e.setName(r.name());
    e.setCurrencyId(r.currencyId());
    var walletType = entityManager.find(com.poscloud.wallet.wallet.WalletTypeDefinition.class, r.walletTypeId());
    ApiException.require(walletType != null && walletType.getScope() == WalletScope.CUSTOMER && walletType.getStatus() == Status.ACTIVE, "INVALID_WALLET_TYPE");
    e.setWalletTypeId(r.walletTypeId());
    e.setSettlementWalletId(r.settlementWalletId());
    e.setAgentRewardMode(r.agentRewardMode());
    e.setAgentRewardType(r.agentRewardType());
    e.setAgentRewardValue(r.agentRewardValue());
    e.setMinimumCommission(r.minimumCommission());
    e.setMaximumCommission(r.maximumCommission());
    e.setStatus(r.status());
  }

  private void validate(Request r, BillerProduct existing) {
    ApiException.require(
        entityManager.find(Biller.class, r.billerId()) != null, "BILLER_NOT_FOUND");
    var w = entityManager.find(com.poscloud.wallet.wallet.Wallet.class, r.settlementWalletId());
    ApiException.require(
        w != null
            && w.getWalletType() == WalletType.BILLER
            && w.getCurrencyId().equals(r.currencyId()),
        "INVALID_SETTLEMENT_WALLET");
    if (r.agentRewardType() == RewardType.PERCENTAGE)
      ApiException.require(
          r.agentRewardValue().compareTo(new BigDecimal("100")) < 0, "INVALID_COMMISSION");
    range(r.minimumCommission(), r.maximumCommission());
    if (existing != null)
      ApiException.require(
          existing.getCode().equals(r.code())
              && existing.getBillerId().equals(r.billerId())
              && existing.getCurrencyId().equals(r.currencyId()),
          "PRODUCT_DEFINITION_IMMUTABLE");
  }

  private void range(BigDecimal min, BigDecimal max) {
    ApiException.require(min == null || max == null || min.compareTo(max) <= 0, "INVALID_RANGE");
  }
}

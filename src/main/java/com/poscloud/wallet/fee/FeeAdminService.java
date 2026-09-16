package com.poscloud.wallet.fee;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.transaction.IdempotencyService;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FeeAdminService {
  private final FeeRepository repository;
  private final AccessService access;
  private final AuditService audit;
  private final EntityManager entityManager;
  private final IdempotencyService json;

  @io.swagger.v3.oas.annotations.media.Schema(name = "FeeAdminServiceRequest")
  public record Request(
      @NotNull UUID transactionTypeId,
      UUID billerProductId,
      CustomerType customerType,
      Boolean agentOnly,
      @NotNull UUID currencyId,
      @NotNull CalculationType calculationType,
      @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal fixedAmount,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 6, fraction = 4) BigDecimal percentage,
      @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal minimumFee,
      @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal maximumFee,
      @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal minTransactionAmount,
      @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal maxTransactionAmount,
      @NotNull Instant effectiveFrom,
      Instant effectiveTo,
      int priority,
      @NotNull Status status) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "FeeAdminServiceView")
  public record View(
      UUID id,
      UUID transactionTypeId,
      UUID billerProductId,
      CustomerType customerType,
      Boolean agentOnly,
      UUID currencyId,
      CalculationType calculationType,
      BigDecimal fixedAmount,
      BigDecimal percentage,
      BigDecimal minimumFee,
      BigDecimal maximumFee,
      BigDecimal minTransactionAmount,
      BigDecimal maxTransactionAmount,
      Instant effectiveFrom,
      Instant effectiveTo,
      int priority,
      Status status) {}

  private View view(Fee e) {
    return new View(
        e.getId(),
        e.getTransactionTypeId(),
        e.getBillerProductId(),
        e.getCustomerType(),
        e.getAgentOnly(),
        e.getCurrencyId(),
        e.getCalculationType(),
        e.getFixedAmount(),
        e.getPercentage(),
        e.getMinimumFee(),
        e.getMaximumFee(),
        e.getMinTransactionAmount(),
        e.getMaxTransactionAmount(),
        e.getEffectiveFrom(),
        e.getEffectiveTo(),
        e.getPriority(),
        e.getStatus());
  }

  public View create(Request r) {
    access.requireStaff();
    validate(r, null);
    var e = new Fee();
    apply(e, r);
    repository.save(e);
    audit.record("FEE_CREATED", "fees", e.getId(), null, json.serialize(view(e)));
    return view(e);
  }

  public View update(UUID id, Request r) {
    access.requireStaff();
    var e = repository.findById(id).orElseThrow(() -> new ApiException("FEE_NOT_FOUND"));
    validate(r, e);
    var before = json.serialize(view(e));
    apply(e, r);
    audit.record("FEE_UPDATED", "fees", id, before, json.serialize(view(e)));
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
    var e = repository.findById(id).orElseThrow(() -> new ApiException("FEE_NOT_FOUND"));
    return view(e);
  }

  public View status(UUID id, boolean active) {
    access.requireStaff();
    var e = repository.findById(id).orElseThrow(() -> new ApiException("FEE_NOT_FOUND"));
    var before = json.serialize(view(e));
    e.setStatus(active ? Status.ACTIVE : Status.INACTIVE);
    audit.record("FEE_STATUS_CHANGED", "fees", id, before, json.serialize(view(e)));
    return view(e);
  }

  private void apply(Fee e, Request r) {
    e.setTransactionTypeId(r.transactionTypeId());
    e.setBillerProductId(r.billerProductId());
    e.setCustomerType(r.customerType());
    e.setAgentOnly(r.agentOnly());
    e.setCurrencyId(r.currencyId());
    e.setCalculationType(r.calculationType());
    e.setFixedAmount(r.fixedAmount());
    e.setPercentage(r.percentage());
    e.setMinimumFee(r.minimumFee());
    e.setMaximumFee(r.maximumFee());
    e.setMinTransactionAmount(r.minTransactionAmount());
    e.setMaxTransactionAmount(r.maxTransactionAmount());
    e.setEffectiveFrom(r.effectiveFrom());
    e.setEffectiveTo(r.effectiveTo());
    e.setPriority(r.priority());
    e.setStatus(r.status());
  }

  private void validate(Request r, Fee existing) {
    ApiException.require(
        entityManager.find(
                com.poscloud.wallet.transaction.TransactionType.class, r.transactionTypeId())
            != null,
        "TRANSACTION_TYPE_NOT_FOUND");
    ApiException.require(
        entityManager.find(com.poscloud.wallet.currency.Currency.class, r.currencyId()) != null,
        "INVALID_CURRENCY");
    if (r.billerProductId() != null) {
      var p =
          entityManager.find(com.poscloud.wallet.biller.BillerProduct.class, r.billerProductId());
      ApiException.require(
          p != null && p.getCurrencyId().equals(r.currencyId()), "PRODUCT_NOT_FOUND");
    }
    if (r.calculationType() != CalculationType.PERCENTAGE)
      ApiException.require(r.fixedAmount() != null, "INVALID_FEE");
    if (r.calculationType() != CalculationType.FIXED)
      ApiException.require(r.percentage() != null, "INVALID_FEE");
    ApiException.require(
        r.effectiveTo() == null || r.effectiveTo().isAfter(r.effectiveFrom()),
        "INVALID_EFFECTIVE_DATES");
    range(r.minimumFee(), r.maximumFee());
    range(r.minTransactionAmount(), r.maxTransactionAmount());
  }

  private void range(BigDecimal min, BigDecimal max) {
    ApiException.require(min == null || max == null || min.compareTo(max) <= 0, "INVALID_RANGE");
  }
}

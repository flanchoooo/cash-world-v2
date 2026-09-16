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
public class BillerAdminService {
  private final BillerRepository repository;
  private final AccessService access;
  private final AuditService audit;
  private final EntityManager entityManager;
  private final IdempotencyService json;

  @io.swagger.v3.oas.annotations.media.Schema(name = "BillerAdminServiceRequest")
  public record Request(
      @NotBlank @Size(max = 200) String code,
      @NotBlank @Size(max = 200) String name,
      @NotNull BillerCategory category,
      @NotNull UUID settlementWalletId,
      @NotNull Status status,
      boolean supportsValidation,
      boolean supportsReversal,
      boolean supportsEnquiry) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "BillerAdminServiceView")
  public record View(
      UUID id,
      String code,
      String name,
      BillerCategory category,
      UUID settlementWalletId,
      Status status,
      boolean supportsValidation,
      boolean supportsReversal,
      boolean supportsEnquiry) {}

  private View view(Biller e) {
    return new View(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getCategory(),
        e.getSettlementWalletId(),
        e.getStatus(),
        e.isSupportsValidation(),
        e.isSupportsReversal(),
        e.isSupportsEnquiry());
  }

  public View create(Request r) {
    access.requireStaff();
    validate(r, null);
    var e = new Biller();
    apply(e, r);
    repository.save(e);
    audit.record("BILLER_CREATED", "billers", e.getId(), null, json.serialize(view(e)));
    return view(e);
  }

  public View update(UUID id, Request r) {
    access.requireStaff();
    var e = repository.findById(id).orElseThrow(() -> new ApiException("BILLER_NOT_FOUND"));
    validate(r, e);
    var before = json.serialize(view(e));
    apply(e, r);
    audit.record("BILLER_UPDATED", "billers", id, before, json.serialize(view(e)));
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
    var e = repository.findById(id).orElseThrow(() -> new ApiException("BILLER_NOT_FOUND"));
    return view(e);
  }

  public View status(UUID id, boolean active) {
    access.requireStaff();
    var e = repository.findById(id).orElseThrow(() -> new ApiException("BILLER_NOT_FOUND"));
    var before = json.serialize(view(e));
    e.setStatus(active ? Status.ACTIVE : Status.INACTIVE);
    audit.record("BILLER_STATUS_CHANGED", "billers", id, before, json.serialize(view(e)));
    return view(e);
  }

  private void apply(Biller e, Request r) {
    e.setCode(r.code());
    e.setName(r.name());
    e.setCategory(r.category());
    e.setSettlementWalletId(r.settlementWalletId());
    e.setStatus(r.status());
    e.setSupportsValidation(r.supportsValidation());
    e.setSupportsReversal(r.supportsReversal());
    e.setSupportsEnquiry(r.supportsEnquiry());
  }

  private void validate(Request r, Biller existing) {
    var w = entityManager.find(com.poscloud.wallet.wallet.Wallet.class, r.settlementWalletId());
    ApiException.require(
        w != null && w.getWalletType() == WalletType.BILLER, "INVALID_SETTLEMENT_WALLET");
    if (existing != null)
      ApiException.require(existing.getCode().equals(r.code()), "BILLER_CODE_IMMUTABLE");
  }

  private void range(BigDecimal min, BigDecimal max) {
    ApiException.require(min == null || max == null || min.compareTo(max) <= 0, "INVALID_RANGE");
  }
}

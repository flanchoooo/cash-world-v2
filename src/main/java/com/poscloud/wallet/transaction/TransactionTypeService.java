package com.poscloud.wallet.transaction;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
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
public class TransactionTypeService {
  private final TransactionTypeRepository repository;
  private final AccessService access;
  private final AuditService audit;
  private final EntityManager entityManager;
  private final IdempotencyService json;

  @io.swagger.v3.oas.annotations.media.Schema(name = "TransactionTypeServiceRequest")
  public record Request(
      @NotBlank @Size(max = 200) String code,
      @NotBlank @Size(max = 200) String name,
      @Size(max = 200) String category,
      boolean isReversible,
      boolean allowsFee,
      boolean allowsCommission,
      @NotNull Status status) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "TransactionTypeServiceView")
  public record View(
      UUID id,
      String code,
      String name,
      String category,
      boolean isReversible,
      boolean allowsFee,
      boolean allowsCommission,
      Status status) {}

  private View view(TransactionType e) {
    return new View(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getCategory(),
        e.isReversible(),
        e.isAllowsFee(),
        e.isAllowsCommission(),
        e.getStatus());
  }

  public View create(Request r) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    validate(r, null);
    var e = new TransactionType();
    apply(e, r);
    repository.save(e);
    audit.record(
        "TRANSACTIONTYPE_CREATED", "transaction-types", e.getId(), null, json.serialize(view(e)));
    return view(e);
  }

  public View update(UUID id, Request r) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    var e =
        repository.findById(id).orElseThrow(() -> new ApiException("TRANSACTIONTYPE_NOT_FOUND"));
    validate(r, e);
    var before = json.serialize(view(e));
    apply(e, r);
    audit.record(
        "TRANSACTIONTYPE_UPDATED", "transaction-types", id, before, json.serialize(view(e)));
    return view(e);
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    access.requireStaff();
    access.requirePermission(Permission.CONFIGURATION_VIEW);
    return repository.findAll().stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    access.requireStaff();
    access.requirePermission(Permission.CONFIGURATION_VIEW);
    var e =
        repository.findById(id).orElseThrow(() -> new ApiException("TRANSACTIONTYPE_NOT_FOUND"));
    return view(e);
  }

  public View status(UUID id, boolean active) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    var e =
        repository.findById(id).orElseThrow(() -> new ApiException("TRANSACTIONTYPE_NOT_FOUND"));
    var before = json.serialize(view(e));
    e.setStatus(active ? Status.ACTIVE : Status.INACTIVE);
    audit.record(
        "TRANSACTIONTYPE_STATUS_CHANGED", "transaction-types", id, before, json.serialize(view(e)));
    return view(e);
  }

  private void apply(TransactionType e, Request r) {
    e.setCode(r.code());
    e.setName(r.name());
    e.setCategory(r.category());
    e.setReversible(r.isReversible());
    e.setAllowsFee(r.allowsFee());
    e.setAllowsCommission(r.allowsCommission());
    e.setStatus(r.status());
  }

  private void validate(Request r, TransactionType existing) {
    ApiException.require(r.code().matches("[A-Z_]{2,40}"), "INVALID_TRANSACTION_TYPE");
    if (existing != null)
      ApiException.require(existing.getCode().equals(r.code()), "TRANSACTION_CODE_IMMUTABLE");
    if (r.code().equals("REVERSAL"))
      ApiException.require(
          !r.isReversible() && !r.allowsFee() && !r.allowsCommission(), "INVALID_REVERSAL_TYPE");
  }

  private void range(BigDecimal min, BigDecimal max) {
    ApiException.require(min == null || max == null || min.compareTo(max) <= 0, "INVALID_RANGE");
  }
}

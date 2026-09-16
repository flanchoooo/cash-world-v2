package com.poscloud.wallet.currency;

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
public class CurrencyService {
  private final CurrencyRepository repository;
  private final AccessService access;
  private final AuditService audit;
  private final EntityManager entityManager;
  private final IdempotencyService json;

  @io.swagger.v3.oas.annotations.media.Schema(name = "CurrencyServiceRequest")
  public record Request(
      @NotBlank @Size(max = 200) String code,
      @NotBlank @Size(max = 200) String name,
      @Size(max = 200) String symbol,
      int decimalPlaces,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 10)
          BigDecimal rateAgainstUsd,
      @NotNull Status status) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "CurrencyServiceView")
  public record View(
      UUID id,
      String code,
      String name,
      String symbol,
      int decimalPlaces,
      BigDecimal rateAgainstUsd,
      Status status) {}

  private View view(Currency e) {
    return new View(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getSymbol(),
        e.getDecimalPlaces(),
        e.getRateAgainstUsd(),
        e.getStatus());
  }

  public View create(Request r) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    validate(r, null);
    var e = new Currency();
    apply(e, r);
    repository.save(e);
    audit.record("CURRENCY_CREATED", "currencies", e.getId(), null, json.serialize(view(e)));
    return view(e);
  }

  public View update(UUID id, Request r) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    var e = repository.findById(id).orElseThrow(() -> new ApiException("CURRENCY_NOT_FOUND"));
    validate(r, e);
    var before = json.serialize(view(e));
    apply(e, r);
    audit.record("CURRENCY_UPDATED", "currencies", id, before, json.serialize(view(e)));
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
    var e = repository.findById(id).orElseThrow(() -> new ApiException("CURRENCY_NOT_FOUND"));
    return view(e);
  }

  public View status(UUID id, boolean active) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    var e = repository.findById(id).orElseThrow(() -> new ApiException("CURRENCY_NOT_FOUND"));
    var before = json.serialize(view(e));
    e.setStatus(active ? Status.ACTIVE : Status.INACTIVE);
    audit.record("CURRENCY_STATUS_CHANGED", "currencies", id, before, json.serialize(view(e)));
    return view(e);
  }

  private void apply(Currency e, Request r) {
    e.setCode(r.code());
    e.setName(r.name());
    e.setSymbol(r.symbol());
    e.setDecimalPlaces(r.decimalPlaces());
    e.setRateAgainstUsd(r.rateAgainstUsd());
    e.setStatus(r.status());
  }

  private void validate(Request r, Currency existing) {
    ApiException.require(
        r.code().matches("[A-Z]{3}")
            && r.decimalPlaces() >= 0
            && r.decimalPlaces() <= 4
            && r.rateAgainstUsd() != null
            && r.rateAgainstUsd().signum() > 0,
        "INVALID_CURRENCY");
    if ("USD".equals(r.code()))
      ApiException.require(r.rateAgainstUsd().compareTo(BigDecimal.ONE) == 0, "INVALID_CURRENCY");
    if (existing != null)
      ApiException.require(
          existing.getCode().equals(r.code()) && existing.getDecimalPlaces() == r.decimalPlaces(),
          "CURRENCY_DEFINITION_IMMUTABLE");
  }

  private void range(BigDecimal min, BigDecimal max) {
    ApiException.require(min == null || max == null || min.compareTo(max) <= 0, "INVALID_RANGE");
  }
}

package com.poscloud.wallet.biller;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.Status;
import com.poscloud.wallet.currency.CurrencyRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommissionPlanService {
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private final ProductCommissionPlanRepository plans;
  private final BillerProductRepository products;
  private final BillerRepository billers;
  private final CurrencyRepository currencies;
  private final AccessService access;
  private final AuditService audit;

  public record Request(
      @NotNull UUID billerProductId,
      @NotBlank @Size(max = 100) String arrangementName,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal totalCommissionPercentage,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal agentCommissionPercentage,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal platformCommissionPercentage,
      @NotNull Status status) {}

  public record View(
      UUID id,
      UUID billerProductId,
      String productCode,
      String productName,
      String biller,
      String currency,
      String arrangementName,
      BigDecimal totalCommissionPercentage,
      BigDecimal agentCommissionPercentage,
      BigDecimal platformCommissionPercentage,
      Status status) {}

  public View create(Request request) {
    access.requireStaff();
    validate(request);
    var plan = new ProductCommissionPlan();
    apply(plan, request);
    plans.save(plan);
    audit.record("PRODUCT_COMMISSION_PLAN_CREATED", "product-commission-plans", plan.getId());
    return view(plan);
  }

  public View update(UUID id, Request request) {
    access.requireStaff();
    var plan = find(id);
    ApiException.require(
        plan.getBillerProductId().equals(request.billerProductId()),
        "PRODUCT_PLAN_PRODUCT_IMMUTABLE");
    validate(request);
    apply(plan, request);
    audit.record("PRODUCT_COMMISSION_PLAN_UPDATED", "product-commission-plans", id);
    return view(plan);
  }

  public View status(UUID id, Status status) {
    access.requireStaff();
    var plan = find(id);
    plan.setStatus(status);
    audit.record("PRODUCT_COMMISSION_PLAN_STATUS_CHANGED", "product-commission-plans", id);
    return view(plan);
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    access.requireStaff();
    return plans.findAll().stream().map(this::view).toList();
  }

  ProductCommissionPlan find(UUID id) {
    return plans.findById(id).orElseThrow(() -> new ApiException("PRODUCT_PLAN_NOT_FOUND"));
  }

  private void validate(Request request) {
    ApiException.require(products.existsById(request.billerProductId()), "PRODUCT_NOT_FOUND");
    ApiException.require(
        request.totalCommissionPercentage().compareTo(HUNDRED) <= 0
            && request.totalCommissionPercentage().compareTo(
                    request.agentCommissionPercentage().add(request.platformCommissionPercentage()))
                == 0,
        "INVALID_COMMISSION_SPLIT");
  }

  private void apply(ProductCommissionPlan plan, Request request) {
    plan.setBillerProductId(request.billerProductId());
    plan.setArrangementName(request.arrangementName().trim());
    plan.setTotalCommissionPercentage(request.totalCommissionPercentage());
    plan.setAgentCommissionPercentage(request.agentCommissionPercentage());
    plan.setPlatformCommissionPercentage(request.platformCommissionPercentage());
    plan.setStatus(request.status());
  }

  private View view(ProductCommissionPlan plan) {
    var product = products.findById(plan.getBillerProductId()).orElseThrow();
    var biller = billers.findById(product.getBillerId()).orElseThrow();
    var currency = currencies.findById(product.getCurrencyId()).orElseThrow();
    return new View(
        plan.getId(), product.getId(), product.getCode(), product.getName(), biller.getName(),
        currency.getCode(), plan.getArrangementName(), plan.getTotalCommissionPercentage(),
        plan.getAgentCommissionPercentage(), plan.getPlatformCommissionPercentage(), plan.getStatus());
  }
}

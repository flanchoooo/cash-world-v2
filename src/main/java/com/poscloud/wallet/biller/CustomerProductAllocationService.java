package com.poscloud.wallet.biller;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.Status;
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
@Transactional
public class CustomerProductAllocationService {
  private final CustomerProductAllocationRepository allocations;
  private final ProductCommissionPlanService commissionPlans;
  private final CustomerRepository customers;
  private final BillerProductRepository products;
  private final BillerRepository billers;
  private final CurrencyRepository currencies;
  private final AccessService access;
  private final AuditService audit;

  public record CustomerRequest(
      @NotNull UUID commissionPlanId,
      @NotNull Status status) {}

  public record View(
      UUID id,
      UUID customerId,
      String customerNumber,
      UUID commissionPlanId,
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

  public View createForCustomer(String customerNumber, CustomerRequest request) {
    access.requireStaff();
    var customer = customers.findByCustomerNumber(customerNumber)
        .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
    var plan = activePlan(request.commissionPlanId());
    ApiException.require(
        allocations.findByCustomerIdAndBillerProductId(customer.getId(), plan.getBillerProductId()).isEmpty(),
        "PRODUCT_ALREADY_ALLOCATED");
    var allocation = new CustomerProductAllocation();
    apply(allocation, customer.getId(), plan, request.status());
    allocations.save(allocation);
    audit.record("PRODUCT_ALLOCATED", "customer-product-allocations", allocation.getId());
    return view(allocation);
  }

  public View updateForCustomer(String customerNumber, UUID id, CustomerRequest request) {
    access.requireStaff();
    var customer = customers.findByCustomerNumber(customerNumber)
        .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
    var allocation = allocations.findById(id)
        .orElseThrow(() -> new ApiException("ALLOCATION_NOT_FOUND"));
    ApiException.require(allocation.getCustomerId().equals(customer.getId()), "FORBIDDEN");
    var plan = activePlan(request.commissionPlanId());
    ApiException.require(
        allocation.getBillerProductId().equals(plan.getBillerProductId()),
        "ALLOCATION_DEFINITION_IMMUTABLE");
    apply(allocation, customer.getId(), plan, request.status());
    audit.record("PRODUCT_ALLOCATION_UPDATED", "customer-product-allocations", id);
    return view(allocation);
  }

  public View delinkFromCustomer(String customerNumber, UUID id) {
    access.requireStaff();
    var customer =
        customers
            .findByCustomerNumber(customerNumber)
            .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
    var allocation =
        allocations.findById(id).orElseThrow(() -> new ApiException("ALLOCATION_NOT_FOUND"));
    ApiException.require(allocation.getCustomerId().equals(customer.getId()), "FORBIDDEN");
    var result = view(allocation);
    allocations.delete(allocation);
    audit.record("PRODUCT_DELINKED", "customer-product-allocations", id);
    return result;
  }

  public View status(UUID id, Status status) {
    access.requireStaff();
    var allocation = allocations.findById(id).orElseThrow(() -> new ApiException("ALLOCATION_NOT_FOUND"));
    allocation.setStatus(status);
    audit.record("PRODUCT_ALLOCATION_STATUS_CHANGED", "customer-product-allocations", id);
    return view(allocation);
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    access.requireStaff();
    return allocations.findAll().stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public List<View> listForCustomer(String customerNumber) {
    access.requireStaff();
    var customer = customers.findByCustomerNumber(customerNumber)
        .orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
    return allocations.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<View> available(UUID customerId) {
    return allocations.findByCustomerIdAndStatusOrderByCreatedAt(customerId, Status.ACTIVE).stream()
        .filter(a -> products.findById(a.getBillerProductId()).map(p -> p.getStatus() == Status.ACTIVE).orElse(false))
        .filter(a -> commissionPlans.find(a.getCommissionPlanId()).getStatus() == Status.ACTIVE)
        .map(this::view)
        .toList();
  }

  private ProductCommissionPlan activePlan(UUID id) {
    var plan = commissionPlans.find(id);
    ApiException.require(plan.getStatus() == Status.ACTIVE, "PRODUCT_PLAN_INACTIVE");
    return plan;
  }

  private void apply(
      CustomerProductAllocation allocation,
      UUID customerId,
      ProductCommissionPlan plan,
      Status status) {
    allocation.setCustomerId(customerId);
    allocation.setCommissionPlanId(plan.getId());
    allocation.setBillerProductId(plan.getBillerProductId());
    allocation.setArrangementName(plan.getArrangementName());
    allocation.setTotalCommissionPercentage(plan.getTotalCommissionPercentage());
    allocation.setAgentCommissionPercentage(plan.getAgentCommissionPercentage());
    allocation.setPlatformCommissionPercentage(plan.getPlatformCommissionPercentage());
    allocation.setStatus(status);
  }

  private View view(CustomerProductAllocation a) {
    var customer = customers.findById(a.getCustomerId()).orElseThrow();
    var product = products.findById(a.getBillerProductId()).orElseThrow();
    var biller = billers.findById(product.getBillerId()).orElseThrow();
    var currency = currencies.findById(product.getCurrencyId()).orElseThrow();
    return new View(
        a.getId(), a.getCustomerId(), customer.getCustomerNumber(), a.getCommissionPlanId(),
        a.getBillerProductId(),
        product.getCode(), product.getName(), biller.getName(), currency.getCode(),
        a.getArrangementName(), a.getTotalCommissionPercentage(),
        a.getAgentCommissionPercentage(), a.getPlatformCommissionPercentage(), a.getStatus());
  }
}

package com.poscloud.wallet.external;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.*;
import com.poscloud.wallet.biller.BillerProductRepository;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.currency.CurrencyRepository;
import com.poscloud.wallet.customer.CustomerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditSaleAdminService {
  private final ExternalSaleRepository sales;
  private final CustomerRepository customers;
  private final BillerProductRepository products;
  private final CurrencyRepository currencies;
  private final UserRepository users;
  private final AccessService access;
  private final AuditService audit;

  public record View(
      UUID id, String transactionReference, String seller, String product, String currency,
      BigDecimal faceValue, BigDecimal amountDue, String collectingAgentName,
      String collectingAgentMobile, String collectingAgentIdNumber, String creditStatus,
      Instant createdAt, Instant collectedAt, String collectedBy) {}

  @Transactional(readOnly = true)
  public List<View> list() {
    access.requireStaff();
    return sales.findByCreditSaleTrueOrderByCreatedAtDesc().stream().map(this::view).toList();
  }

  public View collected(UUID id) {
    access.requireStaff();
    var sale = sales.findById(id).orElseThrow(() -> new ApiException("CREDIT_SALE_NOT_FOUND"));
    ApiException.require(sale.isCreditSale(), "CREDIT_SALE_NOT_FOUND");
    ApiException.require("OUTSTANDING".equals(sale.getCreditStatus()), "CREDIT_SALE_ALREADY_SETTLED");
    sale.setCreditStatus("COLLECTED");
    sale.setCollectedAt(Instant.now());
    sale.setCollectedByUserId(access.current().getId());
    audit.record("CREDIT_SALE_COLLECTED", "external-sales", sale.getId());
    return view(sale);
  }

  private View view(ExternalSale sale) {
    var customer = customers.findById(sale.getCustomerId()).orElseThrow();
    var product = products.findById(sale.getBillerProductId()).orElseThrow();
    var currency = currencies.findById(product.getCurrencyId()).orElseThrow();
    var collectedBy = sale.getCollectedByUserId() == null ? null
        : users.findById(sale.getCollectedByUserId()).map(User::getUsername).orElse(null);
    return new View(sale.getId(), sale.getTransactionReference(), customer.getCustomerNumber(),
        product.getName(), currency.getCode(), sale.getFaceValue(), sale.getAmountDue(),
        sale.getCollectingAgentName(), sale.getCollectingAgentMobile(), sale.getCollectingAgentIdNumber(),
        sale.getCreditStatus(), sale.getCreatedAt(), sale.getCollectedAt(), collectedBy);
  }
}

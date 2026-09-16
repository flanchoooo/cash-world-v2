package com.poscloud.wallet.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.auth.AuthService;
import com.poscloud.wallet.biller.*;
import com.poscloud.wallet.billpayment.*;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerRepository;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.*;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExternalWalletService {
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private final AccessService access;
  private final AuthService auth;
  private final CustomerRepository customers;
  private final WalletRepository walletRepository;
  private final WalletService wallets;
  private final BillerService billers;
  private final BillerProductRepository products;
  private final BillerAdapter adapter;
  private final CustomerProductAllocationRepository allocations;
  private final CustomerProductAllocationService allocationViews;
  private final LedgerService ledger;
  private final IdempotencyService idempotency;
  private final TransactionQueryService queries;
  private final ExternalSaleRepository sales;
  private final BillPaymentRepository billPayments;
  private final ReversalService reversals;
  private final ObjectMapper json;

  public record Balance(String currency, BigDecimal availableBalance) {}

  public record SaleRequest(
      @NotBlank @Size(max = 60) String productCode,
      @NotBlank @Size(max = 100) String customerReference,
      @NotBlank @Pattern(regexp = "\\d{4}") String mobilePin,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
          BigDecimal amount,
      boolean creditSale,
      @Size(max = 200) String collectingAgentName,
      @Size(max = 40) String collectingAgentMobile,
      @Size(max = 100) String collectingAgentIdNumber,
      Map<String, Object> metadata) {}

  public record SaleView(
      String transactionReference,
      String reversalTransactionReference,
      String productCode,
      String productName,
      String currency,
      String customerReference,
      BigDecimal faceValue,
      BigDecimal walletDebitAmount,
      BigDecimal totalCommissionAmount,
      BigDecimal agentCommissionAmount,
      BigDecimal platformCommissionAmount,
      boolean creditSale,
      String collectingAgentName,
      String collectingAgentMobile,
      String collectingAgentIdNumber,
      BigDecimal amountDue,
      String creditStatus,
      String providerReference,
      String providerStatus,
      Map<String, Object> requestMetadata,
      Map<String, Object> providerMetadata,
      Instant createdAt) {}

  public record ReversalRequest(
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Pattern(regexp = "\\d{4}") String mobilePin) {}

  @Transactional(readOnly = true)
  public List<Balance> balances() {
    var customerId = customerId();
    var totals = new TreeMap<String, BigDecimal>();
    walletRepository.findByCustomerId(customerId).stream()
        .filter(w -> w.getWalletType() == WalletType.CUSTOMER && w.getStatus() == WalletStatus.ACTIVE)
        .forEach(
            w ->
                totals.merge(
                    wallets.currency(w.getCurrencyId()).getCode(),
                    w.getBalance(),
                    BigDecimal::add));
    return totals.entrySet().stream().map(e -> new Balance(e.getKey(), e.getValue())).toList();
  }

  @Transactional(readOnly = true)
  public List<CustomerProductAllocationService.View> products() {
    return allocationViews.available(customerId());
  }

  public SaleView sale(String key, SaleRequest request) {
    auth.verifyMobilePin(request.mobilePin());
    var result = idempotency.execute(key, "EXTERNAL_SALE", request, t -> purchase(t, request));
    return get(result.transactionReference());
  }

  private TransactionResult purchase(TransactionRecord transaction, SaleRequest request) {
    var customerId = customerId();
    var customer = customers.findById(customerId).orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
    ApiException.require(customer.getStatus() == CustomerStatus.ACTIVE, "CUSTOMER_BLOCKED");
    var product = billers.product(request.productCode());
    var biller = billers.biller(product);
    var allocation = allocations.findByCustomerIdAndBillerProductId(customerId, product.getId())
        .filter(a -> a.getStatus() == Status.ACTIVE)
        .orElseThrow(() -> new ApiException("PRODUCT_NOT_ALLOCATED"));
    var currency = wallets.currency(product.getCurrencyId());
    var amount = Money.amount(request.amount(), currency.getDecimalPlaces());
    var agentCommission = percentage(amount, allocation.getAgentCommissionPercentage(), currency.getDecimalPlaces());
    var platformCommission = percentage(amount, allocation.getPlatformCommissionPercentage(), currency.getDecimalPlaces());
    var totalCommission = agentCommission.add(platformCommission);
    var debit = Money.round(amount.subtract(agentCommission), currency.getDecimalPlaces());
    ApiException.require(debit.signum() >= 0, "INVALID_COMMISSION_SPLIT");
    validateCredit(request);
    var wallet = walletRepository.findByCustomerId(customerId).stream()
        .filter(w -> w.getCurrencyId().equals(product.getCurrencyId()) && w.getWalletType() == WalletType.CUSTOMER)
        .findFirst().orElseThrow(() -> new ApiException("WALLET_NOT_FOUND"));
    wallets.usable(wallet);
    var metadata = new LedgerService.Metadata(
        customerId, customerId, biller.getId(), product.getId(), amount, BigDecimal.ZERO,
        agentCommission, RewardMode.DISCOUNT, null, null, "External sale " + product.getCode(),
        transaction.getIdempotencyKey());
    var postings = new ArrayList<LedgerService.Posting>();
    postings.add(new LedgerService.Posting(wallet.getId(), product.getSettlementWalletId(), debit,
        currency.getId(), EntryType.PRINCIPAL, metadata));
    if (platformCommission.signum() > 0) {
      var platformMetadata = new LedgerService.Metadata(
          customerId, customerId, biller.getId(), product.getId(), amount, BigDecimal.ZERO,
          platformCommission, RewardMode.NONE, null, null,
          "Platform commission " + product.getCode(), transaction.getIdempotencyKey());
      postings.add(new LedgerService.Posting(product.getSettlementWalletId(),
          wallets.system("FEE_REVENUE", currency.getId()).getId(), platformCommission,
          currency.getId(), EntryType.COMMISSION, platformMetadata));
    }
    ledger.checkBatch(postings);
    if (biller.isSupportsValidation())
      ApiException.require(adapter.validateCustomer(product.getCode(), request.customerReference()),
          "INVALID_CUSTOMER_REFERENCE");
    var provider = adapter.purchase(new BillerAdapter.Purchase(
        transaction.getTransactionReference(), product.getCode(), request.customerReference(), amount));
    var sale = new ExternalSale();
    sale.setTransactionReference(transaction.getTransactionReference());
    sale.setCustomerId(customerId);
    sale.setWalletId(wallet.getId());
    sale.setBillerProductId(product.getId());
    sale.setCustomerReference(request.customerReference().trim());
    sale.setFaceValue(amount);
    sale.setWalletDebitAmount(debit);
    sale.setTotalCommissionAmount(totalCommission);
    sale.setAgentCommissionAmount(agentCommission);
    sale.setPlatformCommissionAmount(platformCommission);
    sale.setCreditSale(request.creditSale());
    sale.setCollectingAgentName(trim(request.collectingAgentName()));
    sale.setCollectingAgentMobile(trim(request.collectingAgentMobile()));
    sale.setCollectingAgentIdNumber(trim(request.collectingAgentIdNumber()));
    sale.setAmountDue(
        request.creditSale() && provider.status() != BillerAdapter.State.FAILED ? debit : null);
    sale.setCreditStatus(
        request.creditSale()
            ? switch (provider.status()) {
              case SUCCESS -> "OUTSTANDING";
              case PENDING -> "PENDING";
              case FAILED -> "CANCELLED";
            }
            : null);
    sale.setProviderReference(provider.providerReference());
    sale.setProviderStatus(provider.status().name());
    sale.setRequestMetadata(write(request.metadata() == null ? Map.of() : request.metadata()));
    var providerMetadata = new LinkedHashMap<String, Object>(provider.metadata());
    providerMetadata.put("status", provider.status().name());
    providerMetadata.put(
        "providerReference", Objects.toString(provider.providerReference(), ""));
    providerMetadata.put("message", Objects.toString(provider.message(), ""));
    sale.setProviderMetadata(write(providerMetadata));
    sales.save(sale);
    var payment = new BillPayment();
    payment.setTransactionReference(transaction.getTransactionReference());
    payment.setBillerId(biller.getId());
    payment.setBillerProductId(product.getId());
    payment.setCustomerReference(request.customerReference().trim());
    payment.setProviderReference(provider.providerReference());
    payment.setProviderStatus(provider.status().name());
    payment.setRequestData(write(Map.of(
        "productCode", request.productCode(),
        "customerReference", request.customerReference(),
        "amount", request.amount(),
        "creditSale", request.creditSale(),
        "metadata", request.metadata() == null ? Map.of() : request.metadata())));
    payment.setResponseData(sale.getProviderMetadata());
    billPayments.save(payment);
    if (provider.status() == BillerAdapter.State.SUCCESS) {
      var confirmed = postings.stream().map(p -> {
        var m = p.metadata();
        return new LedgerService.Posting(p.debit(), p.credit(), p.amount(), p.currency(), p.entryType(),
            new LedgerService.Metadata(m.customerId(), m.agentCustomerId(), m.billerId(), m.productId(),
                m.faceValue(), m.feeAmount(), m.commissionAmount(), m.rewardMode(),
                provider.providerReference(), null, m.narration(), m.idempotencyKey()));
      }).toList();
      ledger.postBatch(transaction.getTransactionReference(), "EXTERNAL_SALE", confirmed);
      return queries.result(transaction.getTransactionReference(), null);
    }
    var status = provider.status() == BillerAdapter.State.PENDING ? TransactionStatus.PENDING : TransactionStatus.FAILED;
    return new TransactionResult(transaction.getTransactionReference(), "EXTERNAL_SALE", status,
        amount, BigDecimal.ZERO, agentCommission, currency.getCode(), biller.getId(), product.getId(),
        transaction.getCreatedAt(), status == TransactionStatus.PENDING ? null : Instant.now(), null, List.of());
  }

  @Transactional(readOnly = true)
  public SaleView get(String reference) {
    var sale = sales.findByTransactionReference(reference).orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND"));
    ApiException.require(sale.getCustomerId().equals(customerId()), "FORBIDDEN");
    return view(sale);
  }

  @Transactional(readOnly = true)
  public List<SaleView> history(int offset, int limit) {
    ApiException.require(offset >= 0 && limit > 0 && limit <= 200, "INVALID_PAGE");
    return sales.findByCustomerIdOrderByCreatedAtDesc(customerId()).stream().skip(offset).limit(limit).map(this::view).toList();
  }

  @Transactional
  public SaleView reverse(String reference, String key, ReversalRequest request) {
    auth.verifyMobilePin(request.mobilePin());
    var sale = sales.findByTransactionReference(reference).orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND"));
    ApiException.require(sale.getCustomerId().equals(customerId()), "FORBIDDEN");
    var reversal = reversals.reverseOwned(reference, key, new ReversalService.Request(request.reason()));
    sale.setReversalTransactionReference(reversal.transactionReference());
    sale.setProviderStatus("REVERSED");
    if (sale.isCreditSale()) sale.setCreditStatus("CANCELLED");
    return view(sale);
  }

  private SaleView view(ExternalSale sale) {
    var product = products.findById(sale.getBillerProductId()).orElseThrow(() -> new ApiException("PRODUCT_NOT_FOUND"));
    var currency = wallets.currency(product.getCurrencyId());
    return new SaleView(sale.getTransactionReference(), sale.getReversalTransactionReference(),
        product.getCode(), product.getName(), currency.getCode(), sale.getCustomerReference(),
        sale.getFaceValue(), sale.getWalletDebitAmount(), sale.getTotalCommissionAmount(),
        sale.getAgentCommissionAmount(), sale.getPlatformCommissionAmount(), sale.isCreditSale(),
        sale.getCollectingAgentName(), sale.getCollectingAgentMobile(), sale.getCollectingAgentIdNumber(),
        sale.getAmountDue(), sale.getCreditStatus(), sale.getProviderReference(), sale.getProviderStatus(),
        read(sale.getRequestMetadata()), read(sale.getProviderMetadata()), sale.getCreatedAt());
  }

  private BigDecimal percentage(BigDecimal amount, BigDecimal rate, int places) {
    return amount.multiply(rate).divide(HUNDRED, places, RoundingMode.HALF_UP);
  }

  private void validateCredit(SaleRequest r) {
    if (!r.creditSale()) return;
    ApiException.require(notBlank(r.collectingAgentName()) && notBlank(r.collectingAgentMobile())
        && notBlank(r.collectingAgentIdNumber()), "COLLECTING_AGENT_REQUIRED");
  }

  private UUID customerId() {
    var user = access.current();
    ApiException.require(user.getCustomerId() != null
        && (user.getRole() == Role.CUSTOMER || user.getRole() == Role.AGENT), "FORBIDDEN");
    return user.getCustomerId();
  }

  private boolean notBlank(String value) { return value != null && !value.isBlank(); }
  private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private String write(Object value) {
    try {
      var text = json.writeValueAsString(value);
      ApiException.require(text.length() <= 65535, "METADATA_TOO_LARGE");
      return text;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new ApiException("INVALID_METADATA"); }
  }
  private Map<String, Object> read(String value) {
    try { return json.readValue(value, new TypeReference<>() {}); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
}

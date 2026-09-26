package com.poscloud.wallet.internal;

import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.biller.BillerProduct;
import com.poscloud.wallet.biller.BillerProductRepository;
import com.poscloud.wallet.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poscloud.wallet.common.ApiException;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerRepository;
import com.poscloud.wallet.currency.CurrencyRepository;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.*;
import com.poscloud.wallet.biller.CustomerProductAllocationRepository;
import com.poscloud.wallet.biller.ProductCommissionPlanRepository;
import com.poscloud.wallet.billpayment.BillPayment;
import com.poscloud.wallet.billpayment.BillPaymentRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalWalletService {
  private final AccessService access;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final CustomerRepository customers;
  private final CurrencyRepository currencies;
  private final WalletRepository walletRepository;
  private final WalletService wallets;
  private final LedgerService ledger;
  private final IdempotencyService idempotency;
  private final TransactionQueryService queries;
  private final ReversalService reversals;
  private final BillerProductRepository products;
  private final CustomerProductAllocationRepository allocations;
  private final ProductCommissionPlanRepository commissionPlans;
  private final ObjectMapper json;
  private final BillPaymentRepository billPayments;
  private final TransactionRecordRepository transactionRecords;

  public record Balance(String walletType, String currency, BigDecimal availableBalance,
                        boolean paymentEligible, BigDecimal requiredAmount) {
    public Balance(String walletType, String currency, BigDecimal availableBalance, boolean paymentEligible) {
      this(walletType, currency, availableBalance, paymentEligible, null);
    }
  }

  // Omit currency to return every active customer-wallet balance; supplied currency remains a filter.
  public record BalanceRequest(@NotBlank String customerNumber, String currency,
                               String product, String paymentCurrency, BigDecimal faceValue,
                               @io.swagger.v3.oas.annotations.media.Schema(defaultValue = "0")
                               BigDecimal fee) {
    public BalanceRequest(String customerNumber, String currency) {
      this(customerNumber, currency, null, null, null, BigDecimal.ZERO);
    }
    public BalanceRequest(String customerNumber, String currency, String product, String paymentCurrency) {
      this(customerNumber, currency, product, paymentCurrency, null, BigDecimal.ZERO);
    }
    public BalanceRequest(String customerNumber, String currency, String product,
                          String paymentCurrency, BigDecimal faceValue) {
      this(customerNumber, currency, product, paymentCurrency, faceValue, BigDecimal.ZERO);
    }
    public BalanceRequest {
      if (fee == null) fee = BigDecimal.ZERO;
    }
  }

  public record PinVerificationRequest(
      @NotBlank String customerNumber,
      @NotBlank @Pattern(regexp = "\\d{4}") String customerPin) {}

  public record PinVerificationResult(boolean valid) {}

  public record MutationRequest(
      @NotBlank String requestId,
      @NotBlank String customerNumber,
      @NotBlank String currency,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @Size(max = 500) String reason,
      String product,
      Map<String, Object> apiMetadata,
      @io.swagger.v3.oas.annotations.media.Schema(defaultValue = "0")
      @DecimalMin("0") BigDecimal fee) {
    public MutationRequest(String requestId, String customerNumber, String currency, BigDecimal amount, String reason) {
      this(requestId, customerNumber, currency, amount, reason, null, Map.of(), BigDecimal.ZERO);
    }
    public MutationRequest(String requestId, String customerNumber, String currency, BigDecimal amount, String reason,
                           String product) {
      this(requestId, customerNumber, currency, amount, reason, product, Map.of(), BigDecimal.ZERO);
    }
    public MutationRequest(String requestId, String customerNumber, String currency, BigDecimal amount, String reason,
                           String product, Map<String, Object> apiMetadata) {
      this(requestId, customerNumber, currency, amount, reason, product, apiMetadata, BigDecimal.ZERO);
    }
    public MutationRequest {
      if (fee == null) fee = BigDecimal.ZERO;
    }
  }

  public record ReversalRequest(@NotBlank String requestId, @NotBlank @Size(max = 500) String reason) {}

  @Transactional(readOnly = true)
  public List<Balance> balance(BalanceRequest request) {
    requireService();
    var customer = balanceCustomer(request.customerNumber());
    var customerWallets = activeWallets(customer.getId());
    Wallet paymentWallet = null;
    BigDecimal requiredAmount = null;
    if (request.product() != null && !request.product().isBlank()) {
      ApiException.require(request.paymentCurrency() != null && !request.paymentCurrency().isBlank(), "CURRENCY_REQUIRED");
      var currency = walletCurrency(request.paymentCurrency());
      paymentWallet = selectWallet(customerWallets, currency.getId(), request.product());
      if (request.faceValue() != null) {
        ApiException.require(request.faceValue().signum() > 0, "INVALID_AMOUNT");
        var product = resolveProduct(request.product(), currency.getId());
        var rates = commissionRates(customer.getId(), product);
        int places = wallets.currency(currency.getId()).getDecimalPlaces();
        var commission = percentage(request.faceValue(), rates.customerDiscount(), places);
        requiredAmount = request.faceValue().subtract(commission).add(fee(request.fee(), places));
      }
    }
    var selectedId = paymentWallet == null ? null : paymentWallet.getId();
    var amountRequired = requiredAmount;
    return customerWallets.stream()
        .filter(w -> request.currency() == null || request.currency().isBlank()
            || wallets.currency(w.getCurrencyId()).getCode().equalsIgnoreCase(request.currency().trim()))
        .map(w -> new Balance(wallets.walletType(w.getWalletTypeId()).getCode(),
            wallets.currency(w.getCurrencyId()).getCode(), w.getBalance(), w.getId().equals(selectedId),
            w.getId().equals(selectedId) ? amountRequired : null))
        .toList();
  }

  @Transactional(readOnly = true)
  public PinVerificationResult verifyPin(PinVerificationRequest request) {
    requireService();
    var customer = balanceCustomer(request.customerNumber());
    var user = users.findByCustomerId(customer.getId())
        .orElseThrow(() -> new ApiException("CUSTOMER_USER_NOT_FOUND"));
    ApiException.require(user.getMobilePinHash() != null, "CUSTOMER_PIN_NOT_CONFIGURED");
    ApiException.require(passwords.matches(request.customerPin(), user.getMobilePinHash()), "INVALID_CUSTOMER_PIN");
    return new PinVerificationResult(true);
  }

  public TransactionResult debit(MutationRequest request) {
    return mutate(request, Direction.DEBIT);
  }

  public TransactionResult credit(MutationRequest request) {
    return mutate(request, Direction.CREDIT);
  }

  public TransactionResult reverse(String reference, ReversalRequest request) {
    requireService();
    return reversals.reverseService(reference, request.requestId(), new ReversalService.Request(request.reason()));
  }

  @Transactional
  public void recordProviderResult(String reference, ProviderResult request) {
    requireService();
    var transaction = transactionRecords.lock(reference)
        .orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND"));
    ApiException.require("ESB_WALLET_DEBIT".equals(transaction.getOperation()), "INVALID_TRANSACTION_TYPE");
    var currency = walletCurrency(request.currency());
    var product = resolveProduct(request.product(), currency.getId());
    var payment = billPayments.findByTransactionReference(reference).orElseGet(BillPayment::new);
    payment.setTransactionReference(reference);
    payment.setBillerId(product.getBillerId());
    payment.setBillerProductId(product.getId());
    payment.setCustomerReference(request.customerReference());
    payment.setProviderReference(request.providerReference());
    payment.setProviderStatus(request.providerStatus());
    payment.setRequestData(serializeMetadata(Map.of(
        "apiRequest", request.apiRequestMetadata() == null ? Map.of() : request.apiRequestMetadata(),
        "billerRequest", request.billerRequest() == null ? Map.of() : request.billerRequest())));
    payment.setResponseData(serializeMetadata(request.billerResponse() == null
        ? Map.of() : Map.of("billerResponse", request.billerResponse(), "status", request.providerStatus())));
    billPayments.save(payment);
  }

  public record ProviderResult(@NotBlank String product, @NotBlank String currency,
      @NotBlank String customerReference, String providerReference, @NotBlank String providerStatus,
      Map<String, Object> apiRequestMetadata, Object billerRequest, Object billerResponse) {}

  private TransactionResult mutate(MutationRequest request, Direction direction) {
    requireService();
    return idempotency.execute(request.requestId(), "ESB_WALLET_" + direction, request, t -> {
      var customer = balanceCustomer(request.customerNumber());
      // Payments debit any active customer; preserve the existing standalone credit policy.
      if (direction == Direction.CREDIT) ApiException.require(customer.isAgent(), "CUSTOMER_NOT_AGENT");
      var currency = walletCurrency(request.currency());
      var customerWallet = selectWallet(activeWallets(customer.getId()), currency.getId(), request.product());
      var suspense = wallets.system("SUSPENSE", currency.getId());
      wallets.usable(customerWallet);
      wallets.usable(suspense);
      var faceValue = request.amount();
      var product = request.product() == null || request.product().isBlank()
          ? null : resolveProduct(request.product(), currency.getId());
      int places = wallets.currency(currency.getId()).getDecimalPlaces();
      var fee = fee(request.fee(), places);
      ApiException.require(fee.signum() == 0 || direction == Direction.DEBIT && product != null,
          "FEE_NOT_ALLOWED");
      var commission = BigDecimal.ZERO;
      var platformCommission = BigDecimal.ZERO;
      if (direction == Direction.DEBIT && product != null) {
        var rates = commissionRates(customer.getId(), product);
        commission = percentage(faceValue, rates.customerDiscount(), places);
        platformCommission = percentage(faceValue, rates.platform(), places);
      }
      var debitAmount = faceValue.subtract(commission);
      ApiException.require(debitAmount.signum() > 0, "INVALID_COMMISSION_SPLIT");
      var debit = direction == Direction.DEBIT ? customerWallet : suspense;
      var credit = direction == Direction.DEBIT ? suspense : customerWallet;
      String transactionType = direction == Direction.DEBIT && product != null ? "BILL_PAYMENT" : "ACCOUNT_ADJUSTMENT";
      var metadata = new LedgerService.Metadata(customer.getId(), customer.isAgent() ? customer.getId() : null,
          product == null ? null : product.getBillerId(), product == null ? null : product.getId(),
          faceValue, BigDecimal.ZERO, commission, platformCommission, RewardMode.NONE,
          null, null, request.reason(), request.requestId(), serializeMetadata(request.apiMetadata()));
      var entryType = direction == Direction.DEBIT && product != null
          ? EntryType.PRINCIPAL : EntryType.ADJUSTMENT;
      if (fee.signum() == 0) {
        ledger.postEntry(t.getTransactionReference(), transactionType, debit.getId(), credit.getId(),
            debitAmount, currency.getId(), entryType, metadata);
      } else {
        var feeMetadata = new LedgerService.Metadata(customer.getId(), customer.isAgent() ? customer.getId() : null,
            product.getBillerId(), product.getId(), null, fee, BigDecimal.ZERO, BigDecimal.ZERO,
            RewardMode.NONE, null, null,
            request.reason() == null ? "Payment fee" : request.reason() + " fee", request.requestId(),
            serializeMetadata(request.apiMetadata()));
        ledger.postBatch(t.getTransactionReference(), transactionType, List.of(
            new LedgerService.Posting(debit.getId(), credit.getId(), debitAmount,
                currency.getId(), entryType, metadata),
            new LedgerService.Posting(debit.getId(), credit.getId(), fee,
                currency.getId(), EntryType.FEE, feeMetadata)));
      }
      return queries.result(t.getTransactionReference(), null);
    });
  }

  private BillerProduct resolveProduct(String codeOrName, UUID currencyId) {
    var byCode = products.findByCode(codeOrName.trim())
        .filter(p -> p.getStatus() == Status.ACTIVE && currencyId.equals(p.getCurrencyId()));
    if (byCode.isPresent()) return byCode.get();
    var matches = products.findByNameIgnoreCaseAndCurrencyId(codeOrName.trim(), currencyId).stream()
        .filter(p -> p.getStatus() == Status.ACTIVE).toList();
    ApiException.require(matches.size() == 1, matches.isEmpty() ? "PRODUCT_NOT_FOUND" : "AMBIGUOUS_PRODUCT");
    return matches.get(0);
  }

  private CommissionRates commissionRates(UUID customerId, BillerProduct product) {
    var allocation = allocations.findByCustomerIdAndBillerProductId(customerId, product.getId());
    if (allocation.isPresent()) {
      var configured = allocation.get();
      ApiException.require(configured.getStatus() == Status.ACTIVE, "PRODUCT_NOT_ALLOCATED");
      // Allocations copy plan percentages when created. Use the linked plan so later
      // changes to that plan also apply to customers already assigned to it.
      var plan = commissionPlans.findById(configured.getCommissionPlanId())
          .orElseThrow(() -> new ApiException("PRODUCT_COMMISSION_NOT_CONFIGURED"));
      ApiException.require(plan.getStatus() == Status.ACTIVE
          && product.getId().equals(plan.getBillerProductId()), "PRODUCT_COMMISSION_NOT_CONFIGURED");
      return new CommissionRates(plan.getAgentCommissionPercentage(), plan.getPlatformCommissionPercentage());
    }
    var plan = commissionPlans.findFirstByBillerProductIdAndStatusOrderByCreatedAtDesc(product.getId(), Status.ACTIVE)
        .orElseThrow(() -> new ApiException("PRODUCT_COMMISSION_NOT_CONFIGURED"));
    return new CommissionRates(plan.getAgentCommissionPercentage(), plan.getPlatformCommissionPercentage());
  }

  private BigDecimal percentage(BigDecimal amount, BigDecimal rate, int places) {
    return amount.multiply(rate).divide(new BigDecimal("100"), places, RoundingMode.HALF_UP);
  }

  private BigDecimal fee(BigDecimal amount, int places) {
    ApiException.require(amount != null && amount.signum() >= 0, "INVALID_FEE");
    if (amount.signum() == 0) return BigDecimal.ZERO.setScale(places);
    return com.poscloud.wallet.common.Money.amount(amount, places);
  }

  private String serializeMetadata(Map<String, Object> metadata) {
    try {
      return json.writeValueAsString(metadata == null ? Map.of() : metadata);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new ApiException("INVALID_API_METADATA");
    }
  }

  private record CommissionRates(BigDecimal customerDiscount, BigDecimal platform) {}

  private List<Wallet> activeWallets(UUID customerId) {
    return walletRepository.findByCustomerId(customerId).stream()
        .filter(w -> w.getWalletType() == WalletType.CUSTOMER && w.getStatus() == WalletStatus.ACTIVE)
        .toList();
  }

  /** The same configured product wallet funds validation and debit. Never pool unrelated wallet types. */
  private Wallet selectWallet(List<Wallet> customerWallets, UUID currencyId, String product) {
    UUID walletTypeId = null;
    if (product != null && !product.isBlank()) {
      var byCode = products.findByCode(product.trim())
          .filter(p -> p.getStatus() == Status.ACTIVE && currencyId.equals(p.getCurrencyId()));
      BillerProduct selected;
      if (byCode.isPresent()) {
        selected = byCode.get();
      } else {
        var matches = products.findByNameIgnoreCaseAndCurrencyId(product.trim(), currencyId).stream()
            .filter(p -> p.getStatus() == Status.ACTIVE).toList();
        ApiException.require(!matches.isEmpty(), "PRODUCT_NOT_FOUND");
        ApiException.require(matches.size() == 1, "AMBIGUOUS_PRODUCT");
        selected = matches.get(0);
      }
      walletTypeId = selected.getWalletTypeId();
      ApiException.require(walletTypeId != null, "INVALID_WALLET_TYPE");
    }
    final UUID selectedType = walletTypeId;
    var matches = customerWallets.stream()
        .filter(w -> currencyId.equals(w.getCurrencyId()))
        .filter(w -> selectedType == null || selectedType.equals(w.getWalletTypeId()))
        .toList();
    ApiException.require(!matches.isEmpty(), "WALLET_NOT_FOUND");
    ApiException.require(matches.size() == 1, "WALLET_SELECTION_REQUIRED");
    return matches.get(0);
  }

  private com.poscloud.wallet.customer.Customer balanceCustomer(String number) {
    var c = customers.findByCustomerNumber(number.trim()).orElseThrow(() -> new ApiException("CUSTOMER_NOT_FOUND"));
    ApiException.require(c.getStatus() == CustomerStatus.ACTIVE, "CUSTOMER_BLOCKED");
    return c;
  }

  private com.poscloud.wallet.currency.Currency walletCurrency(String code) {
    return currencies.findByCode(code.trim().toUpperCase(Locale.ROOT))
        .filter(c -> c.getStatus() == Status.ACTIVE)
        .orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
  }

  private void requireService() {
    ApiException.require(access.current().getRole() == Role.ESB_SERVICE, "FORBIDDEN");
  }
}

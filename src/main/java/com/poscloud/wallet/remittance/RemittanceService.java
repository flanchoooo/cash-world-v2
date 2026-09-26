package com.poscloud.wallet.remittance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.auth.UserRepository;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.currency.Currency;
import com.poscloud.wallet.currency.CurrencyRepository;
import com.poscloud.wallet.customer.Customer;
import com.poscloud.wallet.customer.CustomerRepository;
import com.poscloud.wallet.fee.FeeService;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RemittanceService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int MAX_PROOF_BYTES = 5 * 1024 * 1024;
  private static final Set<String> PROOF_TYPES =
      Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

  private final RemittanceRepository remittances;
  private final CurrencyRepository currencies;
  private final CustomerRepository customers;
  private final UserRepository users;
  private final WalletService wallets;
  private final LedgerService ledger;
  private final IdempotencyService idempotency;
  private final TransactionQueryService queries;
  private final FeeService fees;
  private final AccessService access;
  private final ExchangeRateService rates;
  private final AuditService audit;

  @Value("${wallet.public-base-url}")
  private String publicBaseUrl;

  @io.swagger.v3.oas.annotations.media.Schema(name = "RemittanceServiceProofOfPayment")
  public record ProofOfPayment(
      @NotBlank @Size(max = 255) String fileName,
      @NotBlank @Size(max = 100) String contentType,
      @NotBlank @Size(max = 7_000_000) String data) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "RemittanceServiceSend")
  public record Send(
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String sourceCurrency,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String destinationCurrency,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
          BigDecimal amount,
      @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal feeAmount,
      @NotBlank @Size(max = 200) String senderName,
      @NotBlank @Size(max = 40) String senderMobile,
      @NotBlank @Size(max = 100) String senderIdNumber,
      @NotBlank @Size(max = 200) String receiverName,
      @NotBlank @Size(max = 40) String receiverMobile,
      @NotBlank @Size(max = 100) String receiverIdNumber,
      @NotBlank @Size(max = 300) String reasonForSending,
      @NotBlank @Size(max = 200) String sourceOfFunds,
      @Valid ProofOfPayment proofOfPayment) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "RemittanceServicePayout")
  public record Payout(
      @NotBlank @Size(max = 40) String recipientMobile,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String collectionCode) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "RemittanceServiceCashOut")
  public record CashOut(
      @NotBlank @Size(max = 40) String recipientMobile,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String cashOutCode) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "RemittanceServiceQuote")
  public record Quote(
      String senderCurrency,
      BigDecimal sendAmount,
      BigDecimal feeAmount,
      BigDecimal totalToCollect,
      String destinationCurrency,
      BigDecimal convertedFeeAmount,
      BigDecimal exchangeRate,
      BigDecimal recipientReceives,
      boolean feeOverridden) {}

  public record CashOutPreview(
      String remittanceReference,
      String senderName,
      String senderCurrency,
      BigDecimal senderAmount,
      BigDecimal senderFee,
      BigDecimal totalCollected,
      String recipientName,
      String recipientMobile,
      String recipientNationalId,
      String recipientCurrency,
      BigDecimal convertedFee,
      BigDecimal exchangeRate,
      BigDecimal amountToPayOut,
      RemittanceStatus status) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @io.swagger.v3.oas.annotations.media.Schema(name = "RemittanceServiceView")
  public record View(
      String remittanceReference,
      String transactionReference,
      String payoutTransactionReference,
      String senderCurrency,
      String destinationCurrency,
      BigDecimal sendAmount,
      BigDecimal feeAmount,
      BigDecimal destinationFeeAmount,
      BigDecimal totalCollected,
      BigDecimal exchangeRate,
      BigDecimal payoutAmount,
      boolean feeOverridden,
      String senderName,
      String senderMobile,
      String senderIdNumber,
      String receiverName,
      String receiverMobile,
      String receiverIdNumber,
      String reasonForSending,
      String sourceOfFunds,
      String proofOfPaymentName,
      String collectionCode,
      String createdBy,
      String cashedOutBy,
      RemittanceStatus status,
      List<TransactionResult> transactions) {}

  public record Proof(String fileName, String contentType, byte[] data) {}

  public record ReceiptShare(String receiptPath, String receiptUrl) {}

  public record Receipt(String fileName, byte[] data) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SavedDetails(
      String senderName,
      String senderMobile,
      String senderIdNumber,
      String sourceOfFunds,
      String receiverName,
      String receiverMobile,
      String receiverIdNumber) {}

  @Transactional(readOnly = true)
  public SavedDetails savedDetails(String mobile, String party) {
    requireAdministrator(Permission.REMITTANCES_VIEW);
    var normalized = normalizeMobile(mobile);
    ApiException.require(normalized.length() >= 7 && normalized.length() <= 20, "INVALID_MOBILE");
    if ("SENDER".equalsIgnoreCase(party)) {
      var previous = remittances.findLatestSenderByNormalizedMobile(normalized);
      if (previous.isPresent()) {
        var remittance = previous.get();
        return new SavedDetails(
            remittance.getSenderName(),
            remittance.getSenderMobile(),
            remittance.getSenderIdNumber(),
            remittance.getSourceOfFunds(),
            null,
            null,
            null);
      }
      var customer =
          customers
              .findByNormalizedMobile(normalized)
              .orElseThrow(() -> new ApiException("SAVED_DETAILS_NOT_FOUND"));
      return new SavedDetails(
          customerName(customer),
          customer.getMobileNumber(),
          customer.getNationalId(),
          null,
          null,
          null,
          null);
    }
    ApiException.require("RECIPIENT".equalsIgnoreCase(party), "INVALID_PARTY");
    var previous = remittances.findLatestRecipientByNormalizedMobile(normalized);
    if (previous.isPresent()) {
      var remittance = previous.get();
      return new SavedDetails(
          null,
          null,
          null,
          null,
          remittance.getReceiverName(),
          remittance.getReceiverMobile(),
          remittance.getReceiverIdNumber());
    }
    var customer =
        customers
            .findByNormalizedMobile(normalized)
            .orElseThrow(() -> new ApiException("SAVED_DETAILS_NOT_FOUND"));
    return new SavedDetails(
        null,
        null,
        null,
        null,
        customerName(customer),
        customer.getMobileNumber(),
        customer.getNationalId());
  }

  @Transactional(readOnly = true)
  public Quote quote(Send r) {
    requireAdministrator(Permission.REMITTANCE_SEND);
    var source = currency(r.sourceCurrency());
    var destination = currency(r.destinationCurrency());
    var amount = Money.amount(r.amount(), source.getDecimalPlaces());
    var fee = fee(r.feeAmount(), source, amount, currentCustomer());
    var rate = rates.rate(source.getCode(), destination.getCode());
    var payout = Money.round(amount.multiply(rate), destination.getDecimalPlaces());
    Money.amount(payout, destination.getDecimalPlaces());
    var convertedFee = Money.round(fee.multiply(rate), destination.getDecimalPlaces());
    var total = totalToCollect(amount, fee);
    return new Quote(
        source.getCode(),
        amount,
        fee,
        total,
        destination.getCode(),
        convertedFee,
        rate,
        payout,
        r.feeAmount() != null);
  }

  public TransactionResult send(String key, Send r) {
    requireAdministrator(Permission.REMITTANCE_SEND);
    return idempotency.execute(
        key,
        "REMITTANCE_SEND",
        r,
        t -> {
          var actor = access.current();
          var source = currency(r.sourceCurrency());
          var destination = currency(r.destinationCurrency());
          var amount = Money.amount(r.amount(), source.getDecimalPlaces());
          var fee = fee(r.feeAmount(), source, amount, currentCustomer());
          var total = totalToCollect(amount, fee);
          var rate = rates.rate(source.getCode(), destination.getCode());
          var payout = Money.round(amount.multiply(rate), destination.getDecimalPlaces());
          Money.amount(payout, destination.getDecimalPlaces());
          var convertedFee = Money.round(fee.multiply(rate), destination.getDecimalPlaces());
          var proof = decodeProof(r.proofOfPayment());

          var metadata =
              new LedgerService.Metadata(
                  actor.getCustomerId(),
                  null,
                  null,
                  null,
                  amount,
                  fee,
                  BigDecimal.ZERO,
                  RewardMode.NONE,
                  null,
                  null,
                  "Cash remittance send",
                  t.getIdempotencyKey());
          var cash = wallets.system("CASH_SETTLEMENT", source.getId());
          var postings = new ArrayList<LedgerService.Posting>();
          postings.add(
              new LedgerService.Posting(
                  cash.getId(),
                  wallets.system("REMITTANCE_SETTLEMENT", source.getId()).getId(),
                  amount,
                  source.getId(),
                  EntryType.PRINCIPAL,
                  metadata));
          if (fee.signum() > 0)
            postings.add(
                new LedgerService.Posting(
                    cash.getId(),
                    wallets.system("FEE_REVENUE", source.getId()).getId(),
                    fee,
                    source.getId(),
                    EntryType.FEE,
                    metadata));
          ledger.postBatch(t.getTransactionReference(), "REMITTANCE_SEND", postings);

          var remit = new Remittance();
          remit.setRemittanceReference("REM" + UUID.randomUUID().toString().replace("-", ""));
          remit.setTransactionReference(t.getTransactionReference());
          remit.setSenderCustomerId(actor.getCustomerId());
          remit.setCreatedByUserId(actor.getId());
          remit.setSourceCurrencyId(source.getId());
          remit.setDestinationCurrencyId(destination.getId());
          remit.setSendAmount(amount);
          remit.setFeeAmount(fee);
          remit.setDestinationFeeAmount(convertedFee);
          remit.setFeeOverridden(r.feeAmount() != null);
          remit.setExchangeRate(rate);
          remit.setPayoutAmount(payout);
          remit.setSenderName(r.senderName().trim());
          remit.setSenderMobile(r.senderMobile().trim());
          remit.setSenderIdNumber(r.senderIdNumber().trim());
          remit.setReceiverName(r.receiverName().trim());
          remit.setReceiverMobile(r.receiverMobile().trim());
          remit.setReceiverIdNumber(blankToNull(r.receiverIdNumber()));
          remit.setReasonForSending(r.reasonForSending().trim());
          remit.setSourceOfFunds(r.sourceOfFunds().trim());
          remit.setCollectionCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
          if (proof != null) {
            remit.setProofOfPaymentName(proof.fileName());
            remit.setProofOfPaymentContentType(proof.contentType());
            remit.setProofOfPaymentData(proof.data());
          }
          remit.setStatus(RemittanceStatus.AVAILABLE_FOR_PAYOUT);
          recordFx(t, remit, source.getCode(), destination.getCode(), total);
          remittances.save(remit);
          audit.record("REMITTANCE_CREATED", "remittances", remit.getId());
          return queries.result(t.getTransactionReference(), remit.getRemittanceReference());
        });
  }

  public TransactionResult payout(String ref, String key, Payout r) {
    access.requireStaff();
    access.requirePermission(Permission.REMITTANCE_CASHOUT);
    return idempotency.execute(
        key,
        "REMITTANCE_PAYOUT:" + ref,
        r,
        t -> {
          var remit =
              remittances.lock(ref).orElseThrow(() -> new ApiException("REMITTANCE_NOT_FOUND"));
          ApiException.require(
              remit.getStatus() != RemittanceStatus.PAID, "REMITTANCE_ALREADY_PAID");
          ApiException.require(
              remit.getStatus() == RemittanceStatus.AVAILABLE_FOR_PAYOUT,
              "REMITTANCE_NOT_AVAILABLE");
          ApiException.require(
              Objects.equals(remit.getCollectionCode(), r.collectionCode()),
              "REMITTANCE_CODE_MISMATCH");
          ApiException.require(
              normalizeMobile(remit.getReceiverMobile()).equals(normalizeMobile(r.recipientMobile())),
              "REMITTANCE_MOBILE_MISMATCH");

          var source = wallets.currency(remit.getSourceCurrencyId());
          var currency = wallets.currency(remit.getDestinationCurrencyId());
          recordFx(
              t,
              remit,
              source.getCode(),
              currency.getCode(),
              totalToCollect(remit.getSendAmount(), remit.getFeeAmount()));
          var metadata =
              new LedgerService.Metadata(
                  remit.getReceiverCustomerId(),
                  null,
                  null,
                  null,
                  remit.getPayoutAmount(),
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  RewardMode.NONE,
                  null,
                  null,
                  "Cash remittance payout " + ref,
                  t.getIdempotencyKey());
          ledger.postEntry(
              t.getTransactionReference(),
              "REMITTANCE_PAYOUT",
              wallets.system("REMITTANCE_SETTLEMENT", currency.getId()).getId(),
              wallets.system("CASH_SETTLEMENT", currency.getId()).getId(),
              remit.getPayoutAmount(),
              currency.getId(),
              EntryType.PRINCIPAL,
              metadata);
          remit.setPayoutTransactionReference(t.getTransactionReference());
          remit.setCashedOutByUserId(t.getUserId());
          remit.setStatus(RemittanceStatus.PAID);
          remit.setCollectionCode(null);
          audit.record("REMITTANCE_PAID", "remittances", remit.getId());
          return queries.result(t.getTransactionReference(), ref);
        });
  }

  public TransactionResult cashOut(String key, CashOut r) {
    access.requireStaff();
    access.requirePermission(Permission.REMITTANCE_CASHOUT);
    var remittance = cashOutRemittance(r);
    return payout(
        remittance.getRemittanceReference(),
        key,
        new Payout(r.recipientMobile(), r.cashOutCode()));
  }

  @Transactional(readOnly = true)
  public CashOutPreview previewCashOut(CashOut r) {
    access.requireStaff();
    access.requirePermission(Permission.REMITTANCE_CASHOUT);
    var remittance = cashOutRemittance(r);
    ApiException.require(
        remittance.getStatus() == RemittanceStatus.AVAILABLE_FOR_PAYOUT,
        "REMITTANCE_NOT_AVAILABLE");
    var source = currency(remittance.getSourceCurrencyId());
    var destination = currency(remittance.getDestinationCurrencyId());
    return new CashOutPreview(
        remittance.getRemittanceReference(),
        remittance.getSenderName(),
        source.getCode(),
        remittance.getSendAmount(),
        remittance.getFeeAmount(),
        totalToCollect(remittance.getSendAmount(), remittance.getFeeAmount()),
        remittance.getReceiverName(),
        remittance.getReceiverMobile(),
        remittance.getReceiverIdNumber(),
        destination.getCode(),
        remittance.getDestinationFeeAmount(),
        remittance.getExchangeRate(),
        remittance.getPayoutAmount(),
        remittance.getStatus());
  }

  @Transactional(readOnly = true)
  public View get(String ref) {
    var remittance = findAuthorized(ref);
    var source = currency(remittance.getSourceCurrencyId());
    var destination = currency(remittance.getDestinationCurrencyId());
    var createdBy =
        remittance.getCreatedByUserId() == null
            ? null
            : users.findById(remittance.getCreatedByUserId()).map(u -> u.getUsername()).orElse(null);
    var cashedOutBy =
        remittance.getCashedOutByUserId() == null
            ? null
            : users
                .findById(remittance.getCashedOutByUserId())
                .map(u -> u.getUsername())
                .orElse(null);
    var transactions = new ArrayList<TransactionResult>();
    transactions.add(queries.result(remittance.getTransactionReference(), ref));
    if (remittance.getPayoutTransactionReference() != null)
      transactions.add(queries.result(remittance.getPayoutTransactionReference(), ref));
    return new View(
        remittance.getRemittanceReference(),
        remittance.getTransactionReference(),
        remittance.getPayoutTransactionReference(),
        source.getCode(),
        destination.getCode(),
        remittance.getSendAmount(),
        remittance.getFeeAmount(),
        remittance.getDestinationFeeAmount(),
        remittance.getSendAmount().add(zero(remittance.getFeeAmount())),
        remittance.getExchangeRate(),
        remittance.getPayoutAmount(),
        remittance.isFeeOverridden(),
        remittance.getSenderName(),
        remittance.getSenderMobile(),
        remittance.getSenderIdNumber(),
        remittance.getReceiverName(),
        remittance.getReceiverMobile(),
        remittance.getReceiverIdNumber(),
        remittance.getReasonForSending(),
        remittance.getSourceOfFunds(),
        remittance.getProofOfPaymentName(),
        access.current().getRole() == Role.SUPER_ADMIN ? remittance.getCollectionCode() : null,
        createdBy,
        cashedOutBy,
        remittance.getStatus(),
        transactions);
  }

  @Transactional(readOnly = true)
  public Proof proof(String ref) {
    var remittance = findAuthorized(ref);
    ApiException.require(remittance.getProofOfPaymentData() != null, "PROOF_OF_PAYMENT_NOT_FOUND");
    return new Proof(
        remittance.getProofOfPaymentName(),
        remittance.getProofOfPaymentContentType(),
        remittance.getProofOfPaymentData());
  }

  @Transactional
  public ReceiptShare createReceiptShare(String ref) {
    var remittance = findAuthorized(ref);
    if (remittance.getReceiptShareToken() == null) {
      var bytes = new byte[32];
      RANDOM.nextBytes(bytes);
      remittance.setReceiptShareToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
      audit.record("REMITTANCE_RECEIPT_SHARED", "remittances", remittance.getId());
    }
    var path =
        "/api/remittances/public/receipts/" + remittance.getReceiptShareToken() + ".pdf";
    return new ReceiptShare(path, publicBaseUrl.replaceAll("/+$", "") + path);
  }

  @Transactional(readOnly = true)
  public Receipt receipt(String token) {
    ApiException.require(token != null && token.matches("[A-Za-z0-9_-]{43}"), "RECEIPT_NOT_FOUND");
    var remittance =
        remittances
            .findByReceiptShareToken(token)
            .orElseThrow(() -> new ApiException("RECEIPT_NOT_FOUND"));
    var source =
        currencies
            .findById(remittance.getSourceCurrencyId())
            .orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
    var destination =
        currencies
            .findById(remittance.getDestinationCurrencyId())
            .orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
    var createdBy =
        remittance.getCreatedByUserId() == null
            ? null
            : users
                .findById(remittance.getCreatedByUserId())
                .map(user -> user.getUsername())
                .orElse(null);
    return new Receipt(
        "remittance-" + remittance.getRemittanceReference() + ".pdf",
        RemittanceReceiptPdf.create(remittance, source.getCode(), destination.getCode(), createdBy));
  }

  private Remittance findAuthorized(String ref) {
    var remittance =
        remittances
            .findByRemittanceReference(ref)
            .orElseThrow(() -> new ApiException("REMITTANCE_NOT_FOUND"));
    var actor = access.current();
    if (administrator(actor.getRole())) access.requirePermission(Permission.REMITTANCES_VIEW);
    ApiException.require(
        administrator(actor.getRole())
            || Objects.equals(actor.getCustomerId(), remittance.getSenderCustomerId())
            || Objects.equals(actor.getCustomerId(), remittance.getReceiverCustomerId()),
        "FORBIDDEN");
    return remittance;
  }

  private void requireAdministrator(Permission permission) {
    ApiException.require(administrator(access.current().getRole()), "FORBIDDEN");
    access.requirePermission(permission);
  }

  private boolean administrator(Role role) {
    return role == Role.SUPER_ADMIN || role == Role.OPERATIONS || role == Role.CORPORATE_ADMIN;
  }

  private Currency currency(String code) {
    return currencies
        .findByCode(code)
        .filter(c -> c.getStatus() == Status.ACTIVE)
        .orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
  }

  private Currency currency(UUID id) {
    return currencies
        .findById(id)
        .filter(c -> c.getStatus() == Status.ACTIVE)
        .orElseThrow(() -> new ApiException("INVALID_CURRENCY"));
  }

  private Customer currentCustomer() {
    var customerId = access.current().getCustomerId();
    return customerId == null ? null : customers.findById(customerId).orElse(null);
  }

  private BigDecimal fee(
      BigDecimal override, Currency currency, BigDecimal amount, Customer customer) {
    if (override == null)
      return fees.calculateFee("REMITTANCE_SEND", null, customer, currency, amount);
    ApiException.require(override.signum() >= 0 && override.compareTo(Money.MAX) <= 0, "INVALID_FEE");
    try {
      return override.setScale(currency.getDecimalPlaces(), java.math.RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new ApiException("INVALID_FEE");
    }
  }

  private BigDecimal totalToCollect(BigDecimal amount, BigDecimal fee) {
    var total = amount.add(fee);
    ApiException.require(total.compareTo(Money.MAX) <= 0, "BALANCE_LIMIT_EXCEEDED");
    return total;
  }

  private Remittance cashOutRemittance(CashOut request) {
    var matches =
        remittances.findAllByCollectionCode(request.cashOutCode()).stream()
            .filter(
                remittance ->
                    normalizeMobile(remittance.getReceiverMobile())
                        .equals(normalizeMobile(request.recipientMobile())))
            .toList();
    ApiException.require(matches.size() == 1, "REMITTANCE_NOT_FOUND");
    return matches.get(0);
  }

  private void recordFx(
      TransactionRecord transaction,
      Remittance remittance,
      String sourceCurrency,
      String destinationCurrency,
      BigDecimal totalSourceAmount) {
    transaction.setRemittanceReference(remittance.getRemittanceReference());
    transaction.setSourceCurrency(sourceCurrency);
    transaction.setDestinationCurrency(destinationCurrency);
    transaction.setSourceAmount(remittance.getSendAmount());
    transaction.setSourceFeeAmount(remittance.getFeeAmount());
    transaction.setDestinationFeeAmount(remittance.getDestinationFeeAmount());
    transaction.setExchangeRate(remittance.getExchangeRate());
    transaction.setRecipientAmount(remittance.getPayoutAmount());
    transaction.setTotalSourceAmount(totalSourceAmount);
  }

  private Proof decodeProof(ProofOfPayment proof) {
    if (proof == null) return null;
    ApiException.require(PROOF_TYPES.contains(proof.contentType()), "INVALID_PROOF_OF_PAYMENT");
    try {
      var data = Base64.getDecoder().decode(proof.data());
      ApiException.require(data.length > 0 && data.length <= MAX_PROOF_BYTES, "INVALID_PROOF_OF_PAYMENT");
      return new Proof(proof.fileName().trim(), proof.contentType(), data);
    } catch (IllegalArgumentException e) {
      throw new ApiException("INVALID_PROOF_OF_PAYMENT");
    }
  }

  private String normalizeMobile(String mobile) {
    return mobile == null ? "" : mobile.replaceAll("[^0-9]", "");
  }

  private String customerName(Customer customer) {
    if (customer.getCompanyName() != null && !customer.getCompanyName().isBlank())
      return customer.getCompanyName();
    return String.join(
            " ",
            customer.getFirstName() == null ? "" : customer.getFirstName(),
            customer.getLastName() == null ? "" : customer.getLastName())
        .trim();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}

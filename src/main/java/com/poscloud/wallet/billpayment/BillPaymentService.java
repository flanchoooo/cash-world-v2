package com.poscloud.wallet.billpayment;

import com.poscloud.wallet.biller.*;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerRepository;
import com.poscloud.wallet.fee.FeeService;
import com.poscloud.wallet.transaction.*;
import com.poscloud.wallet.wallet.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillPaymentService {
  private final com.poscloud.wallet.commission.RewardService rewards;
  private final WalletService wallets;
  private final BillerService billers;
  private final BillerAdapter adapter;
  private final FeeService fees;
  private final TransactionTypeRepository transactionTypes;
  private final CustomerRepository customers;
  private final LedgerService ledger;
  private final IdempotencyService idempotency;
  private final TransactionQueryService queries;
  private final BillPaymentRepository payments;
  private final TransactionRecordRepository records;
  private final com.fasterxml.jackson.databind.ObjectMapper json;

  @io.swagger.v3.oas.annotations.media.Schema(name = "BillPaymentServiceRequest")
  public record Request(
      @NotBlank String walletNumber,
      @NotBlank String productCode,
      @NotBlank @Size(max = 100) String customerReference,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
          BigDecimal amount) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "BillPaymentServiceSnapshot")
  public record Snapshot(Request request, List<LedgerService.Posting> postings) {}

  public TransactionResult pay(String key, Request r) {
    return idempotency.execute(key, "BILL_PAYMENT", r, t -> purchase(t, r, false));
  }

  @Transactional
  public TransactionResult enquire(String ref) {
    var record = records.lock(ref).orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND"));
    var payment =
        payments
            .findByTransactionReference(ref)
            .orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND"));
    Request r;
    try {
      r = json.readValue(payment.getRequestData(), Snapshot.class).request();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    wallets.owned(wallets.find(r.walletNumber()));
    if (record.getStatus() != TransactionStatus.PENDING) return queries.get(ref);
    var result = purchase(record, r, true);
    record.setStatus(result.status());
    record.setResultData(idempotency.serialize(result));
    return result;
  }

  private TransactionResult purchase(TransactionRecord t, Request r, boolean enquiry) {
    var w = wallets.find(r.walletNumber());
    wallets.owned(w);
    wallets.usable(w);
    ApiException.require(w.getWalletType() == WalletType.CUSTOMER, "INVALID_WALLET_TYPE");
    var customer = customers.findById(w.getCustomerId()).orElseThrow();
    var p = billers.product(r.productCode());
    var b = billers.biller(p);
    ApiException.require(w.getCurrencyId().equals(p.getCurrencyId()), "INVALID_CURRENCY");
    var currency = wallets.currency(w.getCurrencyId());
    Money.amount(r.amount(), currency.getDecimalPlaces());
    var fee = fees.calculateFee("BILL_PAYMENT", p.getId(), customer, currency, r.amount());
    boolean rewarded =
        customer.isAgent()
            && transactionTypes.findByCode("BILL_PAYMENT").orElseThrow().isAllowsCommission();
    BigDecimal commission = rewards.calculate(p, r.amount(), currency.getDecimalPlaces(), rewarded);
    RewardMode mode = rewarded ? p.getAgentRewardMode() : RewardMode.NONE;
    var m =
        new LedgerService.Metadata(
            customer.getId(),
            customer.isAgent() ? customer.getId() : null,
            b.getId(),
            p.getId(),
            r.amount(),
            fee,
            commission,
            mode,
            null,
            null,
            "Bill payment " + p.getCode(),
            t.getIdempotencyKey());
    var postings = new ArrayList<LedgerService.Posting>();
    postings.add(
        new LedgerService.Posting(
            w.getId(),
            p.getSettlementWalletId(),
            mode == RewardMode.DISCOUNT ? r.amount().subtract(commission) : r.amount(),
            currency.getId(),
            EntryType.PRINCIPAL,
            m));
    if (fee.signum() > 0)
      postings.add(
          new LedgerService.Posting(
              w.getId(),
              wallets.system("FEE_REVENUE", currency.getId()).getId(),
              fee,
              currency.getId(),
              EntryType.FEE,
              m));
    if (commission.signum() > 0)
      postings.add(
          new LedgerService.Posting(
              wallets.system("COMMISSION_EXPENSE", currency.getId()).getId(),
              mode == RewardMode.DISCOUNT ? p.getSettlementWalletId() : w.getId(),
              commission,
              currency.getId(),
              EntryType.COMMISSION,
              m));
    if (enquiry) {
      try {
        postings =
            new ArrayList<>(
                json.readValue(
                        payments
                            .findByTransactionReference(t.getTransactionReference())
                            .orElseThrow()
                            .getRequestData(),
                        Snapshot.class)
                    .postings());
        fee = postings.get(0).metadata().feeAmount();
        commission = postings.get(0).metadata().commissionAmount();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    ledger.checkBatch(postings);
    if (b.isSupportsValidation())
      ApiException.require(
          adapter.validateCustomer(p.getCode(), r.customerReference()),
          "INVALID_CUSTOMER_REFERENCE");
    if (enquiry) ApiException.require(b.isSupportsEnquiry(), "ENQUIRY_NOT_SUPPORTED");
    var provider =
        enquiry
            ? adapter.enquire(t.getTransactionReference(), r.customerReference())
            : adapter.purchase(
                new BillerAdapter.Purchase(
                    t.getTransactionReference(), p.getCode(), r.customerReference(), r.amount()));
    var payment =
        payments
            .findByTransactionReference(t.getTransactionReference())
            .orElseGet(BillPayment::new);
    payment.setTransactionReference(t.getTransactionReference());
    payment.setBillerId(b.getId());
    payment.setBillerProductId(p.getId());
    payment.setCustomerReference(r.customerReference());
    payment.setProviderReference(provider.providerReference());
    payment.setProviderStatus(provider.status().name());
    payment.setRequestData(idempotency.serialize(new Snapshot(r, postings)));
    payment.setResponseData(idempotency.serialize(provider));
    payments.save(payment);
    if (provider.status() == BillerAdapter.State.SUCCESS) {
      postings =
          new ArrayList<>(
              postings.stream()
                  .map(
                      posting -> {
                        var meta = posting.metadata();
                        var confirmed =
                            new LedgerService.Metadata(
                                meta.customerId(),
                                meta.agentCustomerId(),
                                meta.billerId(),
                                meta.productId(),
                                meta.faceValue(),
                                meta.feeAmount(),
                                meta.commissionAmount(),
                                meta.rewardMode(),
                                provider.providerReference(),
                                meta.originalReference(),
                                meta.narration(),
                                meta.idempotencyKey());
                        return new LedgerService.Posting(
                            posting.debit(),
                            posting.credit(),
                            posting.amount(),
                            posting.currency(),
                            posting.entryType(),
                            confirmed);
                      })
                  .toList());
      ledger.postBatch(t.getTransactionReference(), "BILL_PAYMENT", postings);
      return queries.result(t.getTransactionReference(), null);
    }
    var status =
        provider.status() == BillerAdapter.State.PENDING
            ? TransactionStatus.PENDING
            : TransactionStatus.FAILED;
    return new TransactionResult(
        t.getTransactionReference(),
        "BILL_PAYMENT",
        status,
        r.amount(),
        fee,
        commission,
        currency.getCode(),
        b.getId(),
        p.getId(),
        t.getCreatedAt(),
        status == TransactionStatus.PENDING ? null : Instant.now(),
        null,
        List.of());
  }
}

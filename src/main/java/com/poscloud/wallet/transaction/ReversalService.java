package com.poscloud.wallet.transaction;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.biller.*;
import com.poscloud.wallet.billpayment.BillPaymentRepository;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.wallet.*;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReversalService {
  private final jakarta.persistence.EntityManager entityManager;
  private final com.poscloud.wallet.remittance.RemittanceRepository remittances;
  private final IdempotencyService idempotency;
  private final TransactionRecordRepository records;
  private final LedgerEntryRepository entries;
  private final TransactionTypeRepository types;
  private final LedgerService ledger;
  private final TransactionQueryService queries;
  private final AccessService access;
  private final AuditService audit;
  private final BillPaymentRepository payments;
  private final BillerRepository billers;
  private final BillerAdapter adapter;

  @io.swagger.v3.oas.annotations.media.Schema(name = "ReversalServiceRequest")
  public record Request(@NotBlank @Size(max = 500) String reason) {}

  public TransactionResult reverse(String ref, String key, Request request) {
    access.requireStaff();
    access.requirePermission(Permission.TRANSACTION_REVERSE);
    return reverseInternal(ref, key, request);
  }

  public TransactionResult reverseService(String ref, String key, Request request) {
    ApiException.require(access.current().getRole() == Role.ESB_SERVICE, "FORBIDDEN");
    return reverseInternal(ref, key, request);
  }

  public TransactionResult reverseOwned(String ref, String key, Request request) {
    var original = records.findByTransactionReference(ref).orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND"));
    ApiException.require(original.getUserId().equals(access.current().getId()), "FORBIDDEN");
    return reverseInternal(ref, key, request);
  }

  private TransactionResult reverseInternal(String ref, String key, Request request) {
    return idempotency.execute(
        key,
        "REVERSAL:" + ref,
        request,
        t -> {
          var original =
              records.lock(ref).orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND"));
          ApiException.require(
              original.getStatus() == TransactionStatus.SUCCESS, "TRANSACTION_NOT_REVERSIBLE");
          ApiException.require(
              records.findByOriginalTransactionReference(ref).isEmpty(),
              "TRANSACTION_ALREADY_REVERSED");
          var rows = entries.findByTransactionReferenceOrderBySequenceNumber(ref);
          ApiException.require(!rows.isEmpty(), "TRANSACTION_NOT_REVERSIBLE");
          var type = types.findById(rows.get(0).getTransactionTypeId()).orElseThrow();
          ApiException.require(
              type.isReversible() && !type.getCode().equals("REVERSAL"),
              "TRANSACTION_NOT_REVERSIBLE");
          var remittance =
              remittances
                  .findByTransactionReference(ref)
                  .or(() -> remittances.findByPayoutTransactionReference(ref))
                  .map(r -> remittances.lock(r.getRemittanceReference()).orElseThrow());
          if (remittance.isPresent()) {
            var r = remittance.get();
            entityManager.refresh(r, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (type.getCode().equals("REMITTANCE_SEND"))
              ApiException.require(
                  r.getStatus() == RemittanceStatus.AVAILABLE_FOR_PAYOUT,
                  "TRANSACTION_NOT_REVERSIBLE");
            else
              ApiException.require(
                  r.getStatus() == RemittanceStatus.PAID
                      && ref.equals(r.getPayoutTransactionReference()),
                  "TRANSACTION_NOT_REVERSIBLE");
          }
          var postings = new ArrayList<LedgerService.Posting>();
          var reverseOrder = new ArrayList<>(rows);
          Collections.reverse(reverseOrder);
          for (var row : reverseOrder) {
            var m =
                new LedgerService.Metadata(
                    row.getCustomerId(),
                    row.getAgentCustomerId(),
                    row.getBillerId(),
                    row.getBillerProductId(),
                    row.getFaceValue(),
                    row.getFeeAmount(),
                    row.getCommissionAmount(),
                    row.getPlatformCommissionAmount(),
                    row.getRewardMode(),
                    row.getProviderReference(),
                    ref,
                    request.reason(),
                    t.getIdempotencyKey(),
                    row.getApiMetadata());
            postings.add(
                new LedgerService.Posting(
                    row.getCreditWalletId(),
                    row.getDebitWalletId(),
                    row.getAmount(),
                    row.getCurrencyId(),
                    EntryType.REVERSAL,
                    m));
          }
          ApiException.require(
              types.findByCode("REVERSAL").orElseThrow().getStatus() == Status.ACTIVE,
              "TRANSACTION_TYPE_NOT_FOUND");
          ledger.checkBatch(postings);
          payments
              .findByTransactionReference(ref)
              .ifPresent(
                  payment -> {
                    var b = billers.findById(payment.getBillerId()).orElseThrow();
                    ApiException.require(b.isSupportsReversal(), "TRANSACTION_NOT_REVERSIBLE");
                    var result =
                        adapter.reverse(
                            t.getTransactionReference(), ref, payment.getProviderReference());
                    ApiException.require(
                        result.status() == BillerAdapter.State.SUCCESS,
                        result.status() == BillerAdapter.State.PENDING
                            ? "PROVIDER_TIMEOUT"
                            : "PROVIDER_ERROR");
                    payment.setProviderStatus("REVERSED");
                  });
          t.setOriginalTransactionReference(ref);
          ledger.postBatch(t.getTransactionReference(), "REVERSAL", postings);
          remittance.ifPresent(
              r -> {
                if (type.getCode().equals("REMITTANCE_SEND"))
                  r.setStatus(RemittanceStatus.REVERSED);
                else {
                  r.setStatus(RemittanceStatus.AVAILABLE_FOR_PAYOUT);
                  r.setPayoutTransactionReference(null);
                }
              });
          audit.record("TRANSACTION_REVERSED", "transaction_requests", ref);
          return queries.result(t.getTransactionReference(), null);
        });
  }
}

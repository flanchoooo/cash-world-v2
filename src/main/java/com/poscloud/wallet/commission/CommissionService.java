package com.poscloud.wallet.commission;

import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.*;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.customer.CustomerService;
import com.poscloud.wallet.transaction.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionService {
  private final LedgerEntryRepository ledger;
  private final TransactionRecordRepository records;
  private final CustomerService customers;
  private final AccessService access;

  @io.swagger.v3.oas.annotations.media.Schema(name = "CommissionServiceView")
  public record View(
      Instant date,
      String transactionReference,
      UUID biller,
      UUID product,
      BigDecimal faceValue,
      BigDecimal commissionAmount,
      RewardMode rewardMode,
      TransactionStatus status,
      UUID currencyId) {}

  public List<View> report(String number, int offset, int limit) {
    if (access.staff()) access.requirePermission(Permission.COMMISSIONS_VIEW);
    ApiException.require(offset >= 0 && limit > 0 && limit <= 200, "INVALID_PAGE");
    return all(number, false).stream().skip(offset).limit(limit).toList();
  }

  private List<View> all(String number, boolean includeUnrewarded) {
    var c = customers.find(number);
    access.customer(c.getId());
    ApiException.require(c.isAgent(), "CUSTOMER_NOT_AGENT");
    var rows =
        includeUnrewarded ? ledger.agentBillPayments(c.getId()) : ledger.commissions(c.getId());
    return rows.stream()
        .map(
            l ->
                new View(
                    l.getCreatedAt(),
                    l.getTransactionReference(),
                    l.getBillerId(),
                    l.getBillerProductId(),
                    l.getFaceValue(),
                    includeUnrewarded ? l.getCommissionAmount() : l.getAmount(),
                    l.getRewardMode(),
                    records.existsByOriginalTransactionReference(l.getTransactionReference())
                        ? TransactionStatus.REVERSED
                        : TransactionStatus.SUCCESS,
                    l.getCurrencyId()))
        .toList();
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "CommissionServiceSummary")
  public record Summary(
      UUID currencyId,
      BigDecimal totalCommission,
      long totalBillPayments,
      BigDecimal totalFaceValue,
      Map<UUID, BigDecimal> commissionByProduct) {}

  public List<Summary> summary(String number) {
    if (access.staff()) access.requirePermission(Permission.COMMISSIONS_VIEW);
    var groups = new LinkedHashMap<UUID, List<View>>();
    all(number, true).stream()
        .filter(v -> v.status() == TransactionStatus.SUCCESS)
        .forEach(v -> groups.computeIfAbsent(v.currencyId(), k -> new ArrayList<>()).add(v));
    return groups.entrySet().stream()
        .map(
            e -> {
              var byProduct = new LinkedHashMap<UUID, BigDecimal>();
              e.getValue()
                  .forEach(
                      v -> byProduct.merge(v.product(), v.commissionAmount(), BigDecimal::add));
              return new Summary(
                  e.getKey(),
                  e.getValue().stream()
                      .map(View::commissionAmount)
                      .reduce(BigDecimal.ZERO, BigDecimal::add),
                  e.getValue().size(),
                  e.getValue().stream()
                      .map(View::faceValue)
                      .reduce(BigDecimal.ZERO, BigDecimal::add),
                  byProduct);
            })
        .toList();
  }
}

package com.poscloud.wallet.admin;

import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RemittanceReportService {
  private static final ZoneId REPORT_ZONE = ZoneId.of("Africa/Harare");

  public enum ReportType {
    RBZ,
    INCOME_BY_CURRENCY,
    PENDING_CASHOUT_BY_CURRENCY,
    CASHED_OUT_BY_CURRENCY,
    DAY_END
  }

  public enum PeriodType {
    DAY,
    WEEK,
    MONTH
  }

  public enum ExportFormat {
    CSV,
    PDF
  }

  public record Column(String key, String label) {}

  public record Report(
      ReportType type,
      String title,
      PeriodType period,
      LocalDate anchor,
      Instant from,
      Instant to,
      List<Column> columns,
      List<Map<String, Object>> rows) {}

  public record Export(String fileName, String contentType, byte[] data) {}

  private final JdbcTemplate jdbc;
  private final AccessService access;

  public Report report(ReportType type, PeriodType period, LocalDate anchor) {
    access.requireStaff();
    var selectedDate = anchor == null ? LocalDate.now(REPORT_ZONE) : anchor;
    var range = range(period, selectedDate);
    var columns = columns(type);
    var rows = rows(type, range.from(), range.to());
    return new Report(
        type,
        title(type),
        period,
        selectedDate,
        range.from(),
        range.to(),
        columns,
        rows);
  }

  public Export export(
      ReportType type, PeriodType period, LocalDate anchor, ExportFormat format) {
    var report = report(type, period, anchor);
    var base = type.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-" + report.anchor();
    if (format == ExportFormat.CSV)
      return new Export(
          base + ".csv", "text/csv;charset=UTF-8", csv(report).getBytes(StandardCharsets.UTF_8));
    return new Export(base + ".pdf", "application/pdf", RemittanceReportPdf.create(report));
  }

  private List<Map<String, Object>> rows(ReportType type, Instant from, Instant to) {
    var start = Timestamp.from(from);
    var end = Timestamp.from(to);
    return switch (type) {
      case RBZ ->
          jdbc.queryForList(
              """
              select r.created_at orderDate,
                     r.remittance_reference remittanceReference,
                     r.sender_name senderName,
                     r.sender_mobile senderMobile,
                     r.sender_id_number senderNationalId,
                     r.receiver_name recipientName,
                     r.receiver_mobile recipientMobile,
                     r.receiver_id_number recipientNationalId,
                     sc.code senderCurrency,
                     r.send_amount senderAmount,
                     r.fee_amount feeAmount,
                     (r.send_amount+r.fee_amount) totalCollected,
                     dc.code recipientCurrency,
                     r.exchange_rate exchangeRate,
                     r.payout_amount recipientAmount,
                     r.status,
                     creator.username createdBy,
                     cashier.username cashedOutBy
              from remittances r
              join currencies sc on sc.id=r.source_currency_id
              join currencies dc on dc.id=r.destination_currency_id
              left join users creator on creator.id=r.created_by_user_id
              left join users cashier on cashier.id=r.cashed_out_by_user_id
              where r.created_at>=? and r.created_at<?
              order by r.created_at,r.remittance_reference
              """,
              start,
              end);
      case INCOME_BY_CURRENCY ->
          jdbc.queryForList(
              """
              select sc.code currency,
                     count(*) orderCount,
                     sum(r.send_amount) sendMoneyTotal,
                     sum(r.fee_amount) incomeGenerated,
                     sum(r.send_amount+r.fee_amount) totalCollected
              from remittances r
              join currencies sc on sc.id=r.source_currency_id
              where r.created_at>=? and r.created_at<?
                and r.status not in ('FAILED','REVERSED')
              group by sc.code
              order by sc.code
              """,
              start,
              end);
      case PENDING_CASHOUT_BY_CURRENCY ->
          jdbc.queryForList(
              """
              select dc.code currency,
                     count(*) pendingOrders,
                     sum(r.payout_amount) pendingPayoutTotal
              from remittances r
              join currencies dc on dc.id=r.destination_currency_id
              where r.created_at>=? and r.created_at<?
                and r.status='AVAILABLE_FOR_PAYOUT'
              group by dc.code
              order by dc.code
              """,
              start,
              end);
      case CASHED_OUT_BY_CURRENCY ->
          jdbc.queryForList(
              """
              select dc.code currency,
                     count(*) cashedOutOrders,
                     sum(r.payout_amount) cashedOutTotal
              from remittances r
              join currencies dc on dc.id=r.destination_currency_id
              join transaction_requests payout
                on payout.transaction_reference=r.payout_transaction_reference
              where payout.created_at>=? and payout.created_at<?
                and r.status='PAID'
              group by dc.code
              order by dc.code
              """,
              start,
              end);
      case DAY_END ->
          jdbc.queryForList(
              """
              select teller,currency,
                     sum(cashOutTotal) cashOutTotal,
                     sum(sendMoneyTotal) sendMoneyTotal
              from (
                select creator.username teller,sc.code currency,
                       cast(0 as decimal(19,4)) cashOutTotal,
                       sum(r.send_amount) sendMoneyTotal
                from remittances r
                join currencies sc on sc.id=r.source_currency_id
                left join users creator on creator.id=r.created_by_user_id
                where r.created_at>=? and r.created_at<?
                  and r.status not in ('FAILED','REVERSED')
                group by creator.username,sc.code
                union all
                select cashier.username teller,dc.code currency,
                       sum(r.payout_amount) cashOutTotal,
                       cast(0 as decimal(19,4)) sendMoneyTotal
                from remittances r
                join currencies dc on dc.id=r.destination_currency_id
                join transaction_requests payout
                  on payout.transaction_reference=r.payout_transaction_reference
                left join users cashier on cashier.id=r.cashed_out_by_user_id
                where payout.created_at>=? and payout.created_at<?
                  and r.status='PAID'
                group by cashier.username,dc.code
              ) totals
              group by teller,currency
              order by teller,currency
              """,
              start,
              end,
              start,
              end);
    };
  }

  private List<Column> columns(ReportType type) {
    return switch (type) {
      case RBZ ->
          List.of(
              c("orderDate", "Order date"),
              c("remittanceReference", "Reference"),
              c("senderName", "Sender name"),
              c("senderMobile", "Sender mobile"),
              c("senderNationalId", "Sender national ID"),
              c("recipientName", "Recipient name"),
              c("recipientMobile", "Recipient mobile"),
              c("recipientNationalId", "Recipient national ID"),
              c("senderCurrency", "Sender currency"),
              c("senderAmount", "Sender amount"),
              c("feeAmount", "Fee"),
              c("totalCollected", "Total collected"),
              c("recipientCurrency", "Recipient currency"),
              c("exchangeRate", "Exchange rate"),
              c("recipientAmount", "Recipient amount"),
              c("status", "Status"),
              c("createdBy", "Created by"),
              c("cashedOutBy", "Cashed out by"));
      case INCOME_BY_CURRENCY ->
          List.of(
              c("currency", "Currency"),
              c("orderCount", "Orders"),
              c("sendMoneyTotal", "Send money total"),
              c("incomeGenerated", "Income generated"),
              c("totalCollected", "Total collected"));
      case PENDING_CASHOUT_BY_CURRENCY ->
          List.of(
              c("currency", "Currency"),
              c("pendingOrders", "Pending orders"),
              c("pendingPayoutTotal", "Pending payout total"));
      case CASHED_OUT_BY_CURRENCY ->
          List.of(
              c("currency", "Currency"),
              c("cashedOutOrders", "Cashed-out orders"),
              c("cashedOutTotal", "Cashed-out total"));
      case DAY_END ->
          List.of(
              c("teller", "Teller"),
              c("currency", "Currency"),
              c("cashOutTotal", "Cash-out total"),
              c("sendMoneyTotal", "Send money total"));
    };
  }

  private String title(ReportType type) {
    return switch (type) {
      case RBZ -> "RBZ remittance report";
      case INCOME_BY_CURRENCY -> "Platform income by currency";
      case PENDING_CASHOUT_BY_CURRENCY -> "Pending cash-out orders by currency";
      case CASHED_OUT_BY_CURRENCY -> "Orders cashed out by currency";
      case DAY_END -> "Teller day-end report";
    };
  }

  private Column c(String key, String label) {
    return new Column(key, label);
  }

  private record Range(Instant from, Instant to) {}

  private Range range(PeriodType period, LocalDate anchor) {
    ApiException.require(period != null, "INVALID_REPORT_PERIOD");
    LocalDate start =
        switch (period) {
          case DAY -> anchor;
          case WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
          case MONTH -> anchor.withDayOfMonth(1);
        };
    LocalDate end =
        switch (period) {
          case DAY -> start.plusDays(1);
          case WEEK -> start.plusWeeks(1);
          case MONTH -> start.plusMonths(1);
        };
    return new Range(start.atStartOfDay(REPORT_ZONE).toInstant(), end.atStartOfDay(REPORT_ZONE).toInstant());
  }

  private String csv(Report report) {
    var output = new StringBuilder("\ufeff");
    output.append(report.columns().stream().map(c -> csvValue(c.label())).reduce((a, b) -> a + "," + b).orElse(""));
    output.append("\r\n");
    for (var row : report.rows()) {
      output.append(
          report.columns().stream()
              .map(c -> csvValue(row.get(c.key())))
              .reduce((a, b) -> a + "," + b)
              .orElse(""));
      output.append("\r\n");
    }
    return output.toString();
  }

  private String csvValue(Object value) {
    var text = value == null ? "" : String.valueOf(value);
    if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}

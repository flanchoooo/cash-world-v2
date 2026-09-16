package com.poscloud.wallet.admin;

import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.ApiException;
import com.poscloud.wallet.common.Types.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministrationService {
  private final JdbcTemplate jdbc;
  private final AccessService access;

  public record Page(List<Map<String, Object>> items, long total, int page, int size) {}

  private record Definition(
      String columns, String from, String search, String status, String owner) {}

  private void administrator() {
    var role = access.current().getRole();
    ApiException.require(
        role == Role.SUPER_ADMIN || role == Role.OPERATIONS || role == Role.CORPORATE_ADMIN,
        "FORBIDDEN");
  }

  private Definition definition(String resource) {
    return switch (resource) {
      case "customers" ->
          new Definition(
              "c.id,c.customer_number customerNumber,c.customer_type customerType,c.first_name firstName,c.last_name lastName,c.company_name companyName,c.mobile_number mobileNumber,c.email,c.address,c.registration_number registrationNumber,c.is_agent isAgent,c.agent_type agentType,c.kyc_status kycStatus,c.status,c.created_at createdAt",
              "customers c",
              "concat_ws(' ',c.customer_number,c.first_name,c.last_name,c.company_name,c.mobile_number)",
              "c.status",
              null);
      case "wallets" ->
          new Definition(
              "w.id,w.wallet_number walletNumber,w.customer_id customerId,c.customer_number customerNumber,w.name,w.wallet_type walletType,cu.code currency,w.balance,w.status,w.created_at createdAt",
              "wallets w join currencies cu on cu.id=w.currency_id left join customers c on c.id=w.customer_id",
              "concat_ws(' ',w.wallet_number,w.name,c.customer_number)",
              "w.status",
              "w.customer_id");
      case "users" ->
          new Definition(
              "u.id,u.username,u.customer_id customerId,c.customer_number customerNumber,u.role,u.status,u.last_login_at lastLoginAt,u.created_at createdAt",
              "users u left join customers c on c.id=u.customer_id",
              "concat_ws(' ',u.username,c.customer_number)",
              "u.status",
              "u.customer_id");
      case "transactions" ->
          new Definition(
              "t.id,t.transaction_reference transactionReference,t.remittance_reference remittanceReference,t.operation transactionType,tu.username performedBy,case when exists(select 1 from transaction_requests rev where rev.original_transaction_reference=t.transaction_reference) then 'REVERSED' else t.status end status,COALESCE(t.source_currency,JSON_UNQUOTE(JSON_EXTRACT(t.result_data,'$.currency'))) senderCurrency,COALESCE(t.source_amount,JSON_UNQUOTE(JSON_EXTRACT(t.result_data,'$.faceValue'))) senderAmount,COALESCE(t.source_fee_amount,JSON_UNQUOTE(JSON_EXTRACT(t.result_data,'$.feeAmount'))) senderFee,t.total_source_amount totalCollected,t.destination_currency recipientCurrency,t.exchange_rate exchangeRate,t.recipient_amount recipientAmount,t.destination_fee_amount convertedFee,t.created_at createdAt",
              "transaction_requests t left join users tu on tu.id=t.user_id",
              "concat_ws(' ',t.transaction_reference,t.remittance_reference,t.operation,t.source_currency,t.destination_currency,tu.username)",
              "case when exists(select 1 from transaction_requests rev where rev.original_transaction_reference=t.transaction_reference) then 'REVERSED' else t.status end",
              null);
      case "bill-payments" ->
          new Definition(
              "b.id,b.transaction_reference transactionReference,p.code productCode,b.customer_reference customerReference,b.provider_reference providerReference,b.provider_status status,b.created_at createdAt",
              "bill_payments b join biller_products p on p.id=b.biller_product_id join transaction_requests t on t.transaction_reference=b.transaction_reference join wallets bw on bw.wallet_number=JSON_UNQUOTE(JSON_EXTRACT(b.request_data,'$.request.walletNumber'))",
              "concat_ws(' ',b.transaction_reference,b.customer_reference,p.code)",
              "b.provider_status",
              "bw.customer_id");
      case "remittances" ->
          new Definition(
              "r.id,r.remittance_reference remittanceReference,r.transaction_reference transactionReference,r.sender_name senderName,r.sender_mobile senderMobile,r.sender_id_number senderNationalId,r.receiver_name receiverName,r.receiver_mobile receiverMobile,r.receiver_id_number recipientNationalId,sc.code sourceCurrency,dc.code destinationCurrency,r.send_amount sendAmount,r.fee_amount feeAmount,(r.send_amount+r.fee_amount) totalCollected,r.destination_fee_amount convertedFeeAmount,r.exchange_rate exchangeRate,r.payout_amount payoutAmount,cu.username createdBy,pu.username cashedOutBy,r.status,r.created_at createdAt",
              "remittances r join currencies sc on sc.id=r.source_currency_id join currencies dc on dc.id=r.destination_currency_id left join users cu on cu.id=r.created_by_user_id left join users pu on pu.id=r.cashed_out_by_user_id",
              "concat_ws(' ',r.remittance_reference,r.sender_name,r.sender_mobile,r.sender_id_number,r.receiver_name,r.receiver_mobile,r.receiver_id_number,cu.username,pu.username)",
              "r.status",
              "r.sender_customer_id");
      case "audit" ->
          new Definition(
              "a.id,a.user_id userId,u.username,a.action,a.entity_type entityType,a.entity_id entityId,a.created_at createdAt",
              "audit_logs a left join users u on u.id=a.user_id",
              "concat_ws(' ',a.action,a.entity_type,a.entity_id,u.username)",
              "a.action",
              null);
      default -> throw new ApiException("RESOURCE_NOT_FOUND");
    };
  }

  public Page list(
      String resource,
      String search,
      String status,
      Instant from,
      Instant to,
      int page,
      int size,
      boolean agents) {
    administrator();
    ApiException.require(
        page >= 0
            && page <= 100000
            && size > 0
            && size <= 100
            && search.length() <= 150
            && status.length() <= 100,
        "INVALID_PAGE");
    ApiException.require(from == null || to == null || !from.isAfter(to), "INVALID_RANGE");
    var d = definition(resource);
    var actor = access.current();
    if (resource.equals("users"))
      ApiException.require(actor.getRole() != Role.OPERATIONS, "FORBIDDEN");
    var clauses = new ArrayList<String>();
    var args = new ArrayList<Object>();
    clauses.add("1=1");
    if (actor.getRole() == Role.CORPORATE_ADMIN) {
      ApiException.require(d.owner() != null && actor.getCustomerId() != null, "FORBIDDEN");
      clauses.add(d.owner() + "=?");
      args.add(actor.getCustomerId().toString());
    }
    if (!search.isBlank()) {
      clauses.add(d.search() + " like ?");
      args.add("%" + search + "%");
    }
    if (!status.isBlank()) {
      clauses.add(d.status() + "=?");
      args.add(status);
    }
    if (agents && resource.equals("customers")) clauses.add("c.is_agent=true");
    String alias = d.from().split(" ")[1];
    if (from != null) {
      clauses.add(alias + ".created_at>=?");
      args.add(Timestamp.from(from));
    }
    if (to != null) {
      clauses.add(alias + ".created_at<?");
      args.add(Timestamp.from(to));
    }
    var where = " where " + String.join(" and ", clauses);
    long total =
        jdbc.queryForObject("select count(*) from " + d.from() + where, Long.class, args.toArray());
    args.add(size);
    args.add(page * size);
    var items =
        jdbc.queryForList(
            "select "
                + d.columns()
                + " from "
                + d.from()
                + where
                + " order by "
                + alias
                + ".created_at desc,"
                + alias
                + ".id desc limit ? offset ?",
            args.toArray());
    return new Page(items, total, page, size);
  }

  public record Dashboard(
      long customers,
      long wallets,
      long transactions,
      long pendingRemittances,
      List<Map<String, Object>> balances,
      boolean mockProvider) {}

  @org.springframework.beans.factory.annotation.Value("${wallet.mock-provider-enabled:false}")
  private boolean mockProvider;

  public Dashboard dashboard() {
    administrator();
    var actor = access.current();
    boolean corp = actor.getRole() == Role.CORPORATE_ADMIN;
    ApiException.require(!corp || actor.getCustomerId() != null, "FORBIDDEN");
    Object[] args = corp ? new Object[] {actor.getCustomerId().toString()} : new Object[] {};
    String own = corp ? " where w.customer_id=?" : "";
    var balances =
        jdbc.queryForList(
            "select c.code currency,count(*) walletCount,sum(w.balance) balance from wallets w join currencies c on c.id=w.currency_id"
                + (corp
                    ? " where w.customer_id=? and w.wallet_type='CUSTOMER'"
                    : " where w.wallet_type='CUSTOMER'")
                + " group by c.code order by c.code",
            args);
    long wc = jdbc.queryForObject("select count(*) from wallets w" + own, Long.class, args);
    long cc = corp ? 1 : jdbc.queryForObject("select count(*) from customers", Long.class);
    long tc =
        jdbc.queryForObject(
            "select count(*) from transaction_requests t join users u on u.id=t.user_id"
                + (corp ? " where u.customer_id=?" : ""),
            Long.class,
            args);
    long rc =
        jdbc.queryForObject(
            "select count(*) from remittances where status='AVAILABLE_FOR_PAYOUT'"
                + (corp ? " and sender_customer_id=?" : ""),
            Long.class,
            args);
    return new Dashboard(cc, wc, tc, rc, balances, mockProvider);
  }

  public record Catalog(
      List<Map<String, Object>> currencies,
      List<Map<String, Object>> products,
      boolean mockProvider) {}

  public Catalog catalog() {
    administrator();
    return new Catalog(
        jdbc.queryForList(
            "select id,code,name,decimal_places decimalPlaces from currencies where status='ACTIVE' order by code"),
        jdbc.queryForList(
            "select p.id,p.code,p.name,c.code currency from biller_products p join currencies c on c.id=p.currency_id join billers b on b.id=p.biller_id where p.status='ACTIVE' and b.status='ACTIVE' and c.status='ACTIVE' order by p.code"),
        mockProvider);
  }
}

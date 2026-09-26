package com.poscloud.wallet.admin;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.ApiException;
import com.poscloud.wallet.common.Types.Role;
import com.poscloud.wallet.common.Types.Permission;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {
  private final JdbcTemplate jdbc;
  private final AccessService access;
  private final AuditService audit;

  public record Request(
      @NotNull LocalDate expenseDate,
      @NotBlank @Size(max = 200) String expenseName,
      @Size(max = 100) String category,
      @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
      @NotBlank @Size(min = 3, max = 3) String currency,
      @NotBlank @Size(max = 150) String requestedBy,
      @Size(max = 200) String vendor,
      @Size(max = 1000) String purpose,
      @Size(max = 150) String reference,
      @Size(max = 500) String receiptReference) {}

  public record View(
      UUID id,
      LocalDate expenseDate,
      String expenseName,
      String category,
      BigDecimal amount,
      String currency,
      String requestedBy,
      String vendor,
      String purpose,
      String reference,
      String receiptReference,
      String status,
      String recordedBy,
      Timestamp createdAt) {}

  public record Page(List<View> items, long total, int page, int size) {}

  private void authorize() {
    access.requireStaff();
    access.requirePermission(Permission.EXPENSES_MANAGE);
  }

  private static final String SELECT =
      "select e.id,e.expense_date,e.expense_name,e.category,e.amount,e.currency_code,e.requested_by,e.vendor,e.purpose,e.expense_reference reference,e.receipt_reference,e.status,u.username recorded_by,e.created_at from business_expenses e join users u on u.id=e.created_by_user_id";

  private View map(Map<String, Object> row) {
    return new View(
        UUID.fromString(row.get("id").toString()),
        ((java.sql.Date) row.get("expense_date")).toLocalDate(),
        (String) row.get("expense_name"), (String) row.get("category"),
        (BigDecimal) row.get("amount"), (String) row.get("currency_code"),
        (String) row.get("requested_by"), (String) row.get("vendor"),
        (String) row.get("purpose"), (String) row.get("reference"),
        (String) row.get("receipt_reference"), (String) row.get("status"),
        (String) row.get("recorded_by"), (Timestamp) row.get("created_at"));
  }

  @Transactional(readOnly = true)
  public Page list(String search, String status, int page, int size) {
    authorize();
    ApiException.require(page >= 0 && page <= 100000 && size > 0 && size <= 100
        && search.length() <= 150 && status.length() <= 20, "INVALID_PAGE");
    var clauses = new ArrayList<String>();
    var args = new ArrayList<Object>();
    if (!search.isBlank()) {
      clauses.add("(e.expense_name like ? or e.requested_by like ? or e.vendor like ? or e.reference like ?)");
      String term = "%" + search + "%";
      args.addAll(List.of(term, term, term, term));
    }
    if (!status.isBlank()) { clauses.add("e.status=?"); args.add(status); }
    String where = clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    long total = jdbc.queryForObject("select count(*) from business_expenses e" + where, Long.class, args.toArray());
    args.add(size); args.add(page * size);
    var rows = jdbc.queryForList(SELECT + where + " order by e.expense_date desc,e.created_at desc limit ? offset ?", args.toArray());
    return new Page(rows.stream().map(this::map).toList(), total, page, size);
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    authorize();
    var rows = jdbc.queryForList(SELECT + " where e.id=?", id.toString());
    if (rows.isEmpty()) throw new ApiException("RESOURCE_NOT_FOUND");
    return map(rows.get(0));
  }

  private View mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    var row = new HashMap<String, Object>();
    for (String key : List.of("id", "expense_date", "expense_name", "category", "amount", "currency_code", "requested_by", "vendor", "purpose", "reference", "receipt_reference", "status", "recorded_by", "created_at")) row.put(key, rs.getObject(key));
    return map(row);
  }

  public View create(Request r) {
    var user = access.current();
    access.requireStaff();
    access.requirePermission(Permission.EXPENSES_MANAGE);
    String currency = r.currency().trim().toUpperCase(Locale.ROOT);
    ApiException.require(jdbc.queryForObject("select count(*) from currencies where code=? and status='ACTIVE'", Integer.class, currency) == 1, "INVALID_CURRENCY");
    UUID id = UUID.randomUUID();
    jdbc.update("insert into business_expenses (id,created_at,updated_at,expense_date,expense_name,category,amount,currency_code,requested_by,vendor,purpose,expense_reference,receipt_reference,status,created_by_user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?,'RECORDED',?)",
        id.toString(), Timestamp.from(java.time.Instant.now()), Timestamp.from(java.time.Instant.now()), r.expenseDate(), r.expenseName().trim(), blankToNull(r.category()), r.amount(), currency, r.requestedBy().trim(), blankToNull(r.vendor()), blankToNull(r.purpose()), blankToNull(r.reference()), blankToNull(r.receiptReference()), user.getId().toString());
    audit.record("EXPENSE_RECORDED", "business_expenses", id);
    return get(id);
  }

  private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

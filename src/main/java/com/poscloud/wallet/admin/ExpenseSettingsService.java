package com.poscloud.wallet.admin;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.ApiException;
import com.poscloud.wallet.common.Types.Permission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseSettingsService {
  private final JdbcTemplate jdbc;
  private final AccessService access;
  private final AuditService audit;

  public record Request(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 50) String employeeNumber,
      @Size(max = 150) String contactName,
      @Size(max = 40) String mobileNumber,
      @Email @Size(max = 254) String email,
      @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

  public record View(
      UUID id,
      String kind,
      String name,
      String employeeNumber,
      String contactName,
      String mobileNumber,
      String email,
      String status) {}

  private record Definition(
      String kind,
      String table,
      String select,
      int nameMaxLength) {}

  private Definition definition(String kind) {
    return switch (kind) {
      case "categories" ->
          new Definition(
              "categories",
              "expense_categories",
              "select id,name,null employee_number,null contact_name,null mobile_number,null email,status from expense_categories",
              100);
      case "vendors" ->
          new Definition(
              "vendors",
              "expense_vendors",
              "select id,name,null employee_number,contact_name,mobile_number,email,status from expense_vendors",
              200);
      case "employees" ->
          new Definition(
              "employees",
              "expense_employees",
              "select id,name,employee_number,null contact_name,mobile_number,email,status from expense_employees",
              150);
      default -> throw new ApiException("RESOURCE_NOT_FOUND");
    };
  }

  private void authorize() {
    access.requireStaff();
    access.requirePermission(Permission.EXPENSES_MANAGE);
  }

  @Transactional(readOnly = true)
  public List<View> list(String kind, boolean activeOnly) {
    authorize();
    var d = definition(kind);
    var where = activeOnly ? " where status='ACTIVE'" : "";
    return jdbc.queryForList(d.select() + where + " order by name")
        .stream()
        .map(row -> map(d.kind(), row))
        .toList();
  }

  @Transactional(readOnly = true)
  public View get(String kind, UUID id) {
    authorize();
    return find(definition(kind), id);
  }

  public View create(String kind, Request request) {
    authorize();
    var d = definition(kind);
    validate(d, request);
    var id = UUID.randomUUID();
    var now = Timestamp.from(Instant.now());
    if (d.kind().equals("categories")) {
      jdbc.update(
          "insert into expense_categories(id,created_at,updated_at,name,status) values (?,?,?,?,?)",
          id.toString(),
          now,
          now,
          request.name().trim(),
          request.status());
    } else if (d.kind().equals("vendors")) {
      jdbc.update(
          "insert into expense_vendors(id,created_at,updated_at,name,contact_name,mobile_number,email,status) values (?,?,?,?,?,?,?,?)",
          id.toString(),
          now,
          now,
          request.name().trim(),
          blankToNull(request.contactName()),
          blankToNull(request.mobileNumber()),
          blankToNull(request.email()),
          request.status());
    } else {
      jdbc.update(
          "insert into expense_employees(id,created_at,updated_at,name,employee_number,mobile_number,email,status) values (?,?,?,?,?,?,?,?)",
          id.toString(),
          now,
          now,
          request.name().trim(),
          blankToNull(request.employeeNumber()),
          blankToNull(request.mobileNumber()),
          blankToNull(request.email()),
          request.status());
    }
    audit.record("EXPENSE_SETTING_CREATED", d.table(), id);
    return find(d, id);
  }

  public View update(String kind, UUID id, Request request) {
    authorize();
    var d = definition(kind);
    validate(d, request);
    find(d, id);
    var now = Timestamp.from(Instant.now());
    if (d.kind().equals("categories")) {
      jdbc.update(
          "update expense_categories set updated_at=?,name=?,status=? where id=?",
          now,
          request.name().trim(),
          request.status(),
          id.toString());
    } else if (d.kind().equals("vendors")) {
      jdbc.update(
          "update expense_vendors set updated_at=?,name=?,contact_name=?,mobile_number=?,email=?,status=? where id=?",
          now,
          request.name().trim(),
          blankToNull(request.contactName()),
          blankToNull(request.mobileNumber()),
          blankToNull(request.email()),
          request.status(),
          id.toString());
    } else {
      jdbc.update(
          "update expense_employees set updated_at=?,name=?,employee_number=?,mobile_number=?,email=?,status=? where id=?",
          now,
          request.name().trim(),
          blankToNull(request.employeeNumber()),
          blankToNull(request.mobileNumber()),
          blankToNull(request.email()),
          request.status(),
          id.toString());
    }
    audit.record("EXPENSE_SETTING_UPDATED", d.table(), id);
    return find(d, id);
  }

  public View status(String kind, UUID id, boolean active) {
    authorize();
    var d = definition(kind);
    find(d, id);
    jdbc.update(
        "update " + d.table() + " set updated_at=?,status=? where id=?",
        Timestamp.from(Instant.now()),
        active ? "ACTIVE" : "INACTIVE",
        id.toString());
    audit.record("EXPENSE_SETTING_STATUS_CHANGED", d.table(), id);
    return find(d, id);
  }

  public void delete(String kind, UUID id) {
    authorize();
    var d = definition(kind);
    find(d, id);
    try {
      jdbc.update("delete from " + d.table() + " where id=?", id.toString());
      audit.record("EXPENSE_SETTING_DELETED", d.table(), id);
    } catch (DataIntegrityViolationException e) {
      throw new ApiException("CONFLICT", "Expense setting is already used");
    }
  }

  private View find(Definition d, UUID id) {
    var rows = jdbc.queryForList(d.select() + " where id=?", id.toString());
    if (rows.isEmpty()) throw new ApiException("RESOURCE_NOT_FOUND");
    return map(d.kind(), rows.get(0));
  }

  private void validate(Definition d, Request request) {
    ApiException.require(request.name().trim().length() <= d.nameMaxLength(), "INVALID_REQUEST");
    ApiException.require(
        request.status().equals("ACTIVE") || request.status().equals("INACTIVE"),
        "INVALID_REQUEST");
  }

  private View map(String kind, Map<String, Object> row) {
    return new View(
        UUID.fromString(row.get("id").toString()),
        kind,
        (String) row.get("name"),
        (String) row.get("employee_number"),
        (String) row.get("contact_name"),
        (String) row.get("mobile_number"),
        (String) row.get("email"),
        (String) row.get("status"));
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}

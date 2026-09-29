package com.poscloud.wallet.admin;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.ApiException;
import com.poscloud.wallet.common.Types.Role;
import com.poscloud.wallet.common.Types.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
      @Size(max = 150) String requestedBy,
      @Size(max = 200) String vendor,
      @Size(max = 1000) String purpose,
      @Size(max = 150) String reference,
      @Size(max = 500) String receiptReference,
      UUID categoryId,
      UUID requestedByEmployeeId,
      UUID vendorId,
      @Size(max = 5) List<@Valid AttachmentUpload> attachments) {}

  public record AttachmentUpload(
      @NotBlank @Size(max = 255) String fileName,
      @NotBlank @Size(max = 100) String contentType,
      @NotBlank String data) {}

  public record View(
      UUID id,
      LocalDate expenseDate,
      String expenseName,
      UUID categoryId,
      String category,
      BigDecimal amount,
      String currency,
      UUID requestedByEmployeeId,
      String requestedBy,
      UUID vendorId,
      String vendor,
      String purpose,
      String reference,
      String receiptReference,
      String status,
      String recordedBy,
      int attachmentCount,
      Timestamp createdAt) {}

  public record AttachmentView(
      UUID id, String fileName, String contentType, long fileSize, Timestamp createdAt) {}

  public record AttachmentDownload(String fileName, String contentType, byte[] data) {}

  public record Page(List<View> items, long total, int page, int size) {}

  private void authorize() {
    access.requireStaff();
    access.requirePermission(Permission.EXPENSES_MANAGE);
  }

  private static final String SELECT =
      "select e.id,e.expense_date,e.expense_name,e.category_id,coalesce(c.name,e.category) category,e.amount,e.currency_code,e.requested_by_employee_id,coalesce(emp.name,e.requested_by) requested_by,e.vendor_id,coalesce(v.name,e.vendor) vendor,e.purpose,e.expense_reference reference,e.receipt_reference,e.status,u.username recorded_by,(select count(*) from expense_attachments a where a.expense_id=e.id) attachment_count,e.created_at from business_expenses e join users u on u.id=e.created_by_user_id left join expense_categories c on c.id=e.category_id left join expense_employees emp on emp.id=e.requested_by_employee_id left join expense_vendors v on v.id=e.vendor_id";

  private View map(Map<String, Object> row) {
    return new View(
        UUID.fromString(row.get("id").toString()),
        localDate(row.get("expense_date")),
        (String) row.get("expense_name"),
        uuid(row.get("category_id")),
        (String) row.get("category"),
        (BigDecimal) row.get("amount"), (String) row.get("currency_code"),
        uuid(row.get("requested_by_employee_id")),
        (String) row.get("requested_by"),
        uuid(row.get("vendor_id")),
        (String) row.get("vendor"),
        (String) row.get("purpose"), (String) row.get("reference"),
        (String) row.get("receipt_reference"), (String) row.get("status"),
        (String) row.get("recorded_by"),
        ((Number) row.get("attachment_count")).intValue(),
        timestamp(row.get("created_at")));
  }

  @Transactional(readOnly = true)
  public Page list(String search, String status, int page, int size) {
    authorize();
    ApiException.require(page >= 0 && page <= 100000 && size > 0 && size <= 100
        && search.length() <= 150 && status.length() <= 20, "INVALID_PAGE");
    var clauses = new ArrayList<String>();
    var args = new ArrayList<Object>();
    if (!search.isBlank()) {
      clauses.add("(e.expense_name like ? or e.requested_by like ? or e.vendor like ? or e.expense_reference like ?)");
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
    return find(id).orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND"));
  }

  private Optional<View> find(UUID id) {
    var rows = jdbc.queryForList(SELECT + " where e.id=?", id.toString());
    return rows.stream().findFirst().map(this::map);
  }

  private View mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    var row = new HashMap<String, Object>();
    for (String key : List.of("id", "expense_date", "expense_name", "category_id", "category", "amount", "currency_code", "requested_by_employee_id", "requested_by", "vendor_id", "vendor", "purpose", "reference", "receipt_reference", "status", "recorded_by", "attachment_count", "created_at")) row.put(key, rs.getObject(key));
    return map(row);
  }

  public View create(Request r) {
    var user = access.current();
    access.requireStaff();
    access.requirePermission(Permission.EXPENSES_MANAGE);
    String currency = r.currency().trim().toUpperCase(Locale.ROOT);
    ApiException.require(jdbc.queryForObject("select count(*) from currencies where code=? and status='ACTIVE'", Integer.class, currency) == 1, "INVALID_CURRENCY");
    String category = r.categoryId() == null
        ? blankToNull(r.category())
        : settingName("expense_categories", r.categoryId());
    String requestedBy = r.requestedByEmployeeId() == null
        ? blankToNull(r.requestedBy())
        : settingName("expense_employees", r.requestedByEmployeeId());
    ApiException.require(requestedBy != null, "INVALID_EXPENSE_EMPLOYEE");
    String vendor = r.vendorId() == null ? blankToNull(r.vendor()) : settingName("expense_vendors", r.vendorId());
    UUID id = UUID.randomUUID();
    var now = Timestamp.from(java.time.Instant.now());
    jdbc.update("insert into business_expenses (id,created_at,updated_at,expense_date,expense_name,category,amount,currency_code,requested_by,vendor,purpose,expense_reference,receipt_reference,status,created_by_user_id,category_id,requested_by_employee_id,vendor_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?,'RECORDED',?,?,?,?)",
        id.toString(), now, now, r.expenseDate(), r.expenseName().trim(), category, r.amount(), currency, requestedBy, vendor, blankToNull(r.purpose()), blankToNull(r.reference()), blankToNull(r.receiptReference()), user.getId().toString(), stringOrNull(r.categoryId()), stringOrNull(r.requestedByEmployeeId()), stringOrNull(r.vendorId()));
    for (var attachment : Optional.ofNullable(r.attachments()).orElse(List.of()))
      saveAttachment(id, user.getId(), attachment);
    audit.record("EXPENSE_RECORDED", "business_expenses", id);
    return find(id).orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND"));
  }

  @Transactional(readOnly = true)
  public List<AttachmentView> attachments(UUID expenseId) {
    authorize();
    find(expenseId).orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND"));
    return jdbc.queryForList(
            "select id,file_name,content_type,file_size,created_at from expense_attachments where expense_id=? order by created_at",
            expenseId.toString())
        .stream()
        .map(
            row ->
                new AttachmentView(
                    UUID.fromString(row.get("id").toString()),
                    (String) row.get("file_name"),
                    (String) row.get("content_type"),
                    ((Number) row.get("file_size")).longValue(),
                    timestamp(row.get("created_at"))))
        .toList();
  }

  @Transactional(readOnly = true)
  public AttachmentDownload download(UUID expenseId, UUID attachmentId) {
    authorize();
    find(expenseId).orElseThrow(() -> new ApiException("RESOURCE_NOT_FOUND"));
    var rows = jdbc.queryForList(
        "select file_name,content_type,data from expense_attachments where expense_id=? and id=?",
        expenseId.toString(), attachmentId.toString());
    if (rows.isEmpty()) throw new ApiException("RESOURCE_NOT_FOUND");
    var row = rows.get(0);
    return new AttachmentDownload(
        (String) row.get("file_name"), (String) row.get("content_type"), (byte[]) row.get("data"));
  }

  private void saveAttachment(UUID expenseId, UUID userId, AttachmentUpload attachment) {
    String contentType = attachment.contentType().toLowerCase(Locale.ROOT);
    ApiException.require(
        Set.of("application/pdf", "image/jpeg", "image/png", "image/webp").contains(contentType),
        "INVALID_RECEIPT");
    byte[] data;
    try {
      data = Base64.getDecoder().decode(attachment.data());
    } catch (IllegalArgumentException e) {
      throw new ApiException("INVALID_RECEIPT");
    }
    ApiException.require(data.length > 0 && data.length <= 5 * 1024 * 1024, "INVALID_RECEIPT");
    jdbc.update(
        "insert into expense_attachments(id,expense_id,file_name,content_type,file_size,data,uploaded_by_user_id,created_at) values (UUID(),?,?,?,?,?,?,?)",
        expenseId.toString(),
        attachment.fileName().trim(),
        contentType,
        data.length,
        data,
        userId.toString(),
        Timestamp.from(java.time.Instant.now()));
  }

  private String settingName(String table, UUID id) {
    var rows = jdbc.queryForList(
        "select name from " + table + " where id=? and status='ACTIVE'", id.toString());
    if (rows.isEmpty()) throw new ApiException("RESOURCE_NOT_FOUND");
    return rows.get(0).get("name").toString();
  }

  private UUID uuid(Object value) {
    return value == null ? null : UUID.fromString(value.toString());
  }

  private LocalDate localDate(Object value) {
    if (value instanceof LocalDate date) return date;
    if (value instanceof java.sql.Date date) return date.toLocalDate();
    throw new IllegalStateException("Unexpected expense date type: " + value.getClass().getName());
  }

  private Timestamp timestamp(Object value) {
    if (value instanceof Timestamp timestamp) return timestamp;
    if (value instanceof LocalDateTime dateTime) return Timestamp.valueOf(dateTime);
    throw new IllegalStateException("Unexpected expense timestamp type: " + value.getClass().getName());
  }

  private String stringOrNull(UUID value) {
    return value == null ? null : value.toString();
  }

  private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

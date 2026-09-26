package com.poscloud.wallet.wallet;

import com.poscloud.wallet.audit.AuditService;
import com.poscloud.wallet.auth.AccessService;
import com.poscloud.wallet.common.ApiException;
import com.poscloud.wallet.common.Types.*;
import com.poscloud.wallet.transaction.IdempotencyService;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletTypeService {
  private final WalletTypeRepository repository;
  private final AccessService access;
  private final AuditService audit;
  private final IdempotencyService json;

  public record Request(
      @NotBlank @Size(max = 40) String code,
      @NotBlank @Size(max = 100) String name,
      @Size(max = 500) String description,
      @NotNull WalletScope scope,
      @NotNull Status status) {}

  public record View(UUID id, String code, String name, String description, WalletScope scope, Status status) {}

  public View create(Request request) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    validate(request);
    ApiException.require(repository.findByCode(request.code().trim().toUpperCase()).isEmpty(), "WALLET_TYPE_EXISTS");
    var entity = new WalletTypeDefinition();
    apply(entity, request);
    repository.save(entity);
    audit.record("WALLET_TYPE_CREATED", "wallet_types", entity.getId(), null, json.serialize(view(entity)));
    return view(entity);
  }

  public View update(UUID id, Request request) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    var entity = repository.findById(id).orElseThrow(() -> new ApiException("WALLET_TYPE_NOT_FOUND"));
    validate(request);
    ApiException.require(entity.getCode().equals(request.code().trim().toUpperCase()), "WALLET_TYPE_CODE_IMMUTABLE");
    var before = json.serialize(view(entity));
    apply(entity, request);
    audit.record("WALLET_TYPE_UPDATED", "wallet_types", id, before, json.serialize(view(entity)));
    return view(entity);
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    access.requireStaff();
    access.requirePermission(Permission.CONFIGURATION_VIEW);
    return repository.findAll().stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    access.requireStaff();
    access.requirePermission(Permission.CONFIGURATION_VIEW);
    return view(repository.findById(id).orElseThrow(() -> new ApiException("WALLET_TYPE_NOT_FOUND")));
  }

  public View status(UUID id, boolean active) {
    ApiException.require(access.current().getRole() == Role.SUPER_ADMIN, "FORBIDDEN");
    var entity = repository.findById(id).orElseThrow(() -> new ApiException("WALLET_TYPE_NOT_FOUND"));
    var before = json.serialize(view(entity));
    entity.setStatus(active ? Status.ACTIVE : Status.INACTIVE);
    audit.record("WALLET_TYPE_STATUS_CHANGED", "wallet_types", id, before, json.serialize(view(entity)));
    return view(entity);
  }

  private void validate(Request request) {
    ApiException.require(request.code().trim().matches("[A-Za-z][A-Za-z0-9_-]{1,39}"), "INVALID_WALLET_TYPE");
  }

  private void apply(WalletTypeDefinition entity, Request request) {
    entity.setCode(request.code().trim().toUpperCase());
    entity.setName(request.name().trim());
    entity.setDescription(request.description());
    entity.setScope(request.scope());
    entity.setStatus(request.status());
  }

  private View view(WalletTypeDefinition entity) {
    return new View(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getScope(), entity.getStatus());
  }
}

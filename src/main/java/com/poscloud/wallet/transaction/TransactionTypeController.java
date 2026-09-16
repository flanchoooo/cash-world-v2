package com.poscloud.wallet.transaction;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/transaction-types")
@RequiredArgsConstructor
public class TransactionTypeController {
  private final TransactionTypeService service;

  @PostMapping
  public TransactionTypeService.View create(@Valid @RequestBody TransactionTypeService.Request r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public TransactionTypeService.View update(
      @PathVariable UUID id, @Valid @RequestBody TransactionTypeService.Request r) {
    return service.update(id, r);
  }

  @GetMapping
  public List<TransactionTypeService.View> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public TransactionTypeService.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/activate")
  public TransactionTypeService.View activate(@PathVariable UUID id) {
    return service.status(id, true);
  }

  @PostMapping("/{id}/deactivate")
  public TransactionTypeService.View deactivate(@PathVariable UUID id) {
    return service.status(id, false);
  }
}

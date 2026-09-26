package com.poscloud.wallet.admin;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/expenses")
@RequiredArgsConstructor
public class ExpenseController {
  private final ExpenseService service;

  @GetMapping
  public ExpenseService.Page list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(search, status, page, size);
  }

  @PostMapping
  public ExpenseService.View create(@Valid @RequestBody ExpenseService.Request request) {
    return service.create(request);
  }

  @GetMapping("/{id}")
  public ExpenseService.View get(@PathVariable UUID id) {
    return service.get(id);
  }
}

package com.poscloud.wallet.admin;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/expense-settings")
@RequiredArgsConstructor
public class ExpenseSettingsController {
  private final ExpenseSettingsService service;

  @GetMapping("/{kind}")
  public List<ExpenseSettingsService.View> list(
      @PathVariable String kind, @RequestParam(defaultValue = "false") boolean active) {
    return service.list(kind, active);
  }

  @PostMapping("/{kind}")
  public ExpenseSettingsService.View create(
      @PathVariable String kind, @Valid @RequestBody ExpenseSettingsService.Request request) {
    return service.create(kind, request);
  }

  @GetMapping("/{kind}/{id}")
  public ExpenseSettingsService.View get(@PathVariable String kind, @PathVariable UUID id) {
    return service.get(kind, id);
  }

  @PutMapping("/{kind}/{id}")
  public ExpenseSettingsService.View update(
      @PathVariable String kind,
      @PathVariable UUID id,
      @Valid @RequestBody ExpenseSettingsService.Request request) {
    return service.update(kind, id, request);
  }

  @PostMapping("/{kind}/{id}/activate")
  public ExpenseSettingsService.View activate(@PathVariable String kind, @PathVariable UUID id) {
    return service.status(kind, id, true);
  }

  @PostMapping("/{kind}/{id}/deactivate")
  public ExpenseSettingsService.View deactivate(@PathVariable String kind, @PathVariable UUID id) {
    return service.status(kind, id, false);
  }

  @DeleteMapping("/{kind}/{id}")
  public void delete(@PathVariable String kind, @PathVariable UUID id) {
    service.delete(kind, id);
  }
}

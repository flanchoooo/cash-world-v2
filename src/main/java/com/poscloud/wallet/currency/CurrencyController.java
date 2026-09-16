package com.poscloud.wallet.currency;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/currencies")
@RequiredArgsConstructor
public class CurrencyController {
  private final CurrencyService service;

  @PostMapping
  public CurrencyService.View create(@Valid @RequestBody CurrencyService.Request r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public CurrencyService.View update(
      @PathVariable UUID id, @Valid @RequestBody CurrencyService.Request r) {
    return service.update(id, r);
  }

  @GetMapping
  public List<CurrencyService.View> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public CurrencyService.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/activate")
  public CurrencyService.View activate(@PathVariable UUID id) {
    return service.status(id, true);
  }

  @PostMapping("/{id}/deactivate")
  public CurrencyService.View deactivate(@PathVariable UUID id) {
    return service.status(id, false);
  }
}

package com.poscloud.wallet.wallet;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system-wallets")
@RequiredArgsConstructor
public class SystemWalletController {
  private final SystemWalletService service;

  @PostMapping
  public SystemWalletService.View create(@Valid @RequestBody SystemWalletService.Request r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public SystemWalletService.View update(
      @PathVariable UUID id, @Valid @RequestBody SystemWalletService.Request r) {
    return service.update(id, r);
  }

  @GetMapping
  public List<SystemWalletService.View> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public SystemWalletService.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/activate")
  public SystemWalletService.View activate(@PathVariable UUID id) {
    return service.status(id, true);
  }

  @PostMapping("/{id}/deactivate")
  public SystemWalletService.View deactivate(@PathVariable UUID id) {
    return service.status(id, false);
  }
}

package com.poscloud.wallet.wallet;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/wallet-types")
@RequiredArgsConstructor
public class WalletTypeController {
  private final WalletTypeService service;

  @PostMapping public WalletTypeService.View create(@Valid @RequestBody WalletTypeService.Request r) { return service.create(r); }
  @PutMapping("/{id}") public WalletTypeService.View update(@PathVariable UUID id, @Valid @RequestBody WalletTypeService.Request r) { return service.update(id, r); }
  @GetMapping public List<WalletTypeService.View> list() { return service.list(); }
  @GetMapping("/{id}") public WalletTypeService.View get(@PathVariable UUID id) { return service.get(id); }
  @PostMapping("/{id}/activate") public WalletTypeService.View activate(@PathVariable UUID id) { return service.status(id, true); }
  @PostMapping("/{id}/deactivate") public WalletTypeService.View deactivate(@PathVariable UUID id) { return service.status(id, false); }
}

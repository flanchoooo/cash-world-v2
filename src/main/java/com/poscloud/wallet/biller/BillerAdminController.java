package com.poscloud.wallet.biller;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/billers")
@RequiredArgsConstructor
public class BillerAdminController {
  private final BillerAdminService service;

  @PostMapping
  public BillerAdminService.View create(@Valid @RequestBody BillerAdminService.Request r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public BillerAdminService.View update(
      @PathVariable UUID id, @Valid @RequestBody BillerAdminService.Request r) {
    return service.update(id, r);
  }

  @GetMapping
  public List<BillerAdminService.View> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public BillerAdminService.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/activate")
  public BillerAdminService.View activate(@PathVariable UUID id) {
    return service.status(id, true);
  }

  @PostMapping("/{id}/deactivate")
  public BillerAdminService.View deactivate(@PathVariable UUID id) {
    return service.status(id, false);
  }
}

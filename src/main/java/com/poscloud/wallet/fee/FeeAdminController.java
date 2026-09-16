package com.poscloud.wallet.fee;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/fees")
@RequiredArgsConstructor
public class FeeAdminController {
  private final FeeAdminService service;

  @PostMapping
  public FeeAdminService.View create(@Valid @RequestBody FeeAdminService.Request r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public FeeAdminService.View update(
      @PathVariable UUID id, @Valid @RequestBody FeeAdminService.Request r) {
    return service.update(id, r);
  }

  @GetMapping
  public List<FeeAdminService.View> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public FeeAdminService.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/activate")
  public FeeAdminService.View activate(@PathVariable UUID id) {
    return service.status(id, true);
  }

  @PostMapping("/{id}/deactivate")
  public FeeAdminService.View deactivate(@PathVariable UUID id) {
    return service.status(id, false);
  }
}

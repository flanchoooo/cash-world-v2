package com.poscloud.wallet.biller;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/biller-products")
@RequiredArgsConstructor
public class BillerProductController {
  private final BillerProductService service;

  @PostMapping
  public BillerProductService.View create(@Valid @RequestBody BillerProductService.Request r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  public BillerProductService.View update(
      @PathVariable UUID id, @Valid @RequestBody BillerProductService.Request r) {
    return service.update(id, r);
  }

  @GetMapping
  public List<BillerProductService.View> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public BillerProductService.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/activate")
  public BillerProductService.View activate(@PathVariable UUID id) {
    return service.status(id, true);
  }

  @PostMapping("/{id}/deactivate")
  public BillerProductService.View deactivate(@PathVariable UUID id) {
    return service.status(id, false);
  }
}

package com.poscloud.wallet.admin;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/workspace")
@RequiredArgsConstructor
public class AdministrationController {
  private final AdministrationService service;

  @GetMapping("/dashboard")
  public AdministrationService.Dashboard dashboard() {
    return service.dashboard();
  }

  @GetMapping("/catalog")
  public AdministrationService.Catalog catalog() {
    return service.catalog();
  }

  @GetMapping("/{resource}")
  public AdministrationService.Page list(
      @PathVariable String resource,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "false") boolean agents) {
    return service.list(resource, search, status, from, to, page, size, agents);
  }
}
